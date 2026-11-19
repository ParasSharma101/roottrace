package com.roottrace.infrastructure.kafka;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Disposable broker; no host broker ports, existing services, or persistent user volumes. */
class SecureKafkaIntegrationTest {
    private static Network network;
    private static GenericContainer<?> broker;
    private static Path pki;
    @BeforeAll
    static void start() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        pki=Files.createTempDirectory(Path.of("target"),"kafka-security-").toAbsolutePath();
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var process=new ProcessBuilder(java,root.resolve("scripts/KafkaCertificates.java").toString(),pki.toString()).redirectErrorStream(true).start();
        String generated=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(process.waitFor()).withFailMessage(generated).isZero();
        // Relocate only secret mount paths for this disposable container fixture.
        Map<String,String> files=Map.ofEntries(Map.entry("broker.p12","kafka-broker.p12"),Map.entry("backend.p12","kafka-backend.p12"),
                Map.entry("admin.p12","kafka-admin.p12"),Map.entry("truststore.p12","kafka-truststore.p12"),Map.entry("cluster.id","kafka-cluster-id"),
                Map.entry("broker.properties","kafka-broker.properties"),Map.entry("admin.properties","kafka-admin.properties"),
                Map.entry("backend.properties","kafka-backend.properties"),Map.entry("trust-only.properties","kafka-trust-only.properties"));
        for(String name:List.of("broker.properties","admin.properties","backend.properties","trust-only.properties")) {
            Path file=pki.resolve(name); Files.writeString(file,Files.readString(file).replace("/run/secrets/","/tmp/"));
        }
        for(String name:List.of("start-broker.sh","bootstrap-acls.sh")) {
            Files.writeString(pki.resolve(name),Files.readString(root.resolve("ops/kafka/"+name)).replace("/run/secrets/","/tmp/"));
        }
        network=Network.newNetwork();
        broker=new GenericContainer<>("apache/kafka:3.7.0").withNetwork(network).withNetworkAliases("kafka","wrong-host")
                .withEnv("KAFKA_HEAP_OPTS","-Xms128m -Xmx256m")
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/bash"))
                .withCommand("/tmp/start-broker.sh").waitingFor(Wait.forLogMessage(".*Kafka Server started.*\\n",1)).withStartupTimeout(Duration.ofSeconds(150));
        for(var file:files.entrySet()) broker.withCopyFileToContainer(MountableFile.forHostPath(pki.resolve(file.getKey()),0444),"/tmp/"+file.getValue());
        broker.withCopyFileToContainer(MountableFile.forHostPath(pki.resolve("start-broker.sh"),0444),"/tmp/start-broker.sh")
                .withCopyFileToContainer(MountableFile.forHostPath(pki.resolve("bootstrap-acls.sh"),0444),"/tmp/bootstrap-acls.sh");
        try {
            broker.start();
            var initialized=broker.execInContainer("/bin/bash","/tmp/bootstrap-acls.sh");
            assertThat(initialized.getExitCode()).withFailMessage(initialized.getStderr()+initialized.getStdout()).isZero();
        } catch(Exception | AssertionError failure) {
            if(broker!=null) broker.close();
            network.close();
            throw failure;
        }
    }
    @AfterAll static void stop() { if(broker!=null) broker.close(); if(network!=null) network.close(); }

    @Test
    void generatedSpringConfigurationBindsToAuthenticatedTls() throws Exception {
        var source=new Properties();
        try(var input=Files.newInputStream(pki.resolve("backend-spring.properties"))) { source.load(input); }
        Map<String,Object> values=new HashMap<>(); source.forEach((key,value) -> values.put(key.toString(),value));
        var settings=new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values))
                .bind("spring.kafka",org.springframework.boot.context.properties.bind.Bindable.of(org.springframework.boot.autoconfigure.kafka.KafkaProperties.class)).get();
        var producer=settings.buildProducerProperties(null);
        assertThat(producer.get("security.protocol")).isEqualTo("SSL");
        assertThat(producer.get("ssl.keystore.type")).isEqualTo("PKCS12");
        assertThat(producer.get("ssl.keystore.location").toString()).endsWith("kafka-backend.p12");
        assertThat(producer.get("ssl.endpoint.identification.algorithm")).isEqualTo("https");
        assertThat(producer.get("enable.idempotence").toString()).isEqualTo("true");
        assertThat(settings.getAdmin().isAutoCreate()).isFalse();
    }
    @Test
    void backendCertificateCanPublishAndConsumeOnlyAuthorizedWork() throws Exception {
        var sent=broker.execInContainer("/bin/bash","-c","printf 'roottrace-secure-fixture\\n' | timeout 20s /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:29092 --topic raw-logs --producer.config /tmp/kafka-backend.properties --sync");
        assertThat(sent.getExitCode()).withFailMessage(sent.getStderr()).isZero();
        var received=broker.execInContainer("/bin/bash","-c","timeout 25s /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 --topic raw-logs --group roottrace-log-processor --from-beginning --max-messages 1 --timeout-ms 15000 --consumer.config /tmp/kafka-backend.properties");
        assertThat(received.getExitCode()).withFailMessage(received.getStderr()).isZero();
        assertThat(received.getStdout()).contains("roottrace-secure-fixture");
        var denied=broker.execInContainer("/bin/bash","-c","timeout 15s /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --command-config /tmp/kafka-backend.properties --create --topic forbidden-admin-topic --partitions 1 --replication-factor 1");
        assertThat(denied.getExitCode()).isNotZero();
        assertThat(denied.getStderr()+denied.getStdout()).containsIgnoringCase("authorization");
    }
    @Test
    void missingClientCertificateAndWrongServerHostnameAreRejected() throws Exception {
        for(String arguments:List.of("kafka:29092 --command-config /tmp/kafka-trust-only.properties","wrong-host:29092 --command-config /tmp/kafka-backend.properties")) {
            var rejected=broker.execInContainer("/bin/bash","-c","timeout 15s /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "+arguments);
            assertThat(rejected.getExitCode()).isNotZero();
            assertThat(rejected.getStderr()+rejected.getStdout()).containsIgnoringCase("SSL");
        }
    }
    @Test
    void existingStorageIsNeverReformattedForADifferentClusterId() throws Exception {
        String original=Files.readString(pki.resolve("cluster.id"));
        Path wrong=pki.resolve("wrong-cluster-id"); Files.writeString(wrong,"different-cluster-id\n");
        broker.copyFileToContainer(MountableFile.forHostPath(wrong,0444),"/tmp/kafka-cluster-id");
        try {
            var refused=broker.execInContainer("/bin/bash","/tmp/start-broker.sh");
            assertThat(refused.getExitCode()).isNotZero();
            assertThat(refused.getStderr()).contains("Refusing to modify broker storage");
        } finally {
            Path restored=pki.resolve("restored-cluster-id"); Files.writeString(restored,original);
            broker.copyFileToContainer(MountableFile.forHostPath(restored,0444),"/tmp/kafka-cluster-id");
        }
    }
}
