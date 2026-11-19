import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Java 21 source launcher: java scripts/KafkaCertificates.java OUTPUT_DIRECTORY */
public class KafkaCertificates {
    private static Path out;
    private static final String KEYTOOL=Path.of(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Provide an empty output directory");
        out=Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(out);
        try(var files=Files.list(out)) { if(files.findAny().isPresent()) throw new IllegalArgumentException("Output directory must be empty; existing PKI is never overwritten"); }
        ownerOnly(out,true);
        String ca=password("ca"), trust=password("truststore");
        tool("-genkeypair","-alias","ca","-keyalg","RSA","-keysize","3072","-dname","CN=RootTrace Kafka CA",
                "-validity","3650","-ext","BC=ca:true","-ext","KU=keyCertSign,cRLSign","-storetype","PKCS12",
                "-keystore",file("ca.p12"),"-storepass:file",file("ca.password"));
        tool("-exportcert","-rfc","-alias","ca","-keystore",file("ca.p12"),"-storepass:file",file("ca.password"),"-file",file("ca.crt"));
        tool("-importcert","-noprompt","-alias","ca","-file",file("ca.crt"),"-storetype","PKCS12",
                "-keystore",file("truststore.p12"),"-storepass:file",file("truststore.password"));
        String broker=identity("broker","CN=roottrace-broker",true);
        String admin=identity("admin","CN=roottrace-admin",false);
        String backend=identity("backend","CN=roottrace-backend",false);
        byte[] cluster=new byte[16]; new SecureRandom().nextBytes(cluster);
        write("cluster.id",Base64.getUrlEncoder().withoutPadding().encodeToString(cluster)+"\n");
        write("broker.properties","""
            process.roles=broker,controller
            node.id=1
            controller.quorum.voters=1@kafka:9093
            listeners=SSL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093
            advertised.listeners=SSL://kafka:29092
            listener.security.protocol.map=SSL:SSL,CONTROLLER:SSL
            controller.listener.names=CONTROLLER
            inter.broker.listener.name=SSL
            log.dirs=/var/lib/kafka/data
            ssl.client.auth=required
            ssl.enabled.protocols=TLSv1.2,TLSv1.3
            ssl.keystore.type=PKCS12
            ssl.keystore.location=/run/secrets/kafka-broker.p12
            ssl.keystore.password=%s
            ssl.key.password=%s
            ssl.truststore.type=PKCS12
            ssl.truststore.location=/run/secrets/kafka-truststore.p12
            ssl.truststore.password=%s
            ssl.endpoint.identification.algorithm=https
            authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer
            super.users=User:CN=roottrace-broker;User:CN=roottrace-admin
            allow.everyone.if.no.acl.found=false
            auto.create.topics.enable=false
            offsets.topic.replication.factor=1
            transaction.state.log.replication.factor=1
            transaction.state.log.min.isr=1
            num.partitions=3
            group.initial.rebalance.delay.ms=0
            """.formatted(broker,broker,trust));
        write("broker-client.properties",client("broker",broker,trust));
        write("admin.properties",client("admin",admin,trust));
        write("backend.properties",client("backend",backend,trust));
        write("trust-only.properties","security.protocol=SSL\nssl.truststore.type=PKCS12\nssl.truststore.location=/run/secrets/kafka-truststore.p12\nssl.truststore.password="+trust+"\nssl.endpoint.identification.algorithm=https\nrequest.timeout.ms=5000\ndefault.api.timeout.ms=10000\n");
        write("backend-spring.properties","""
            spring.kafka.security.protocol=SSL
            spring.kafka.admin.auto-create=false
            spring.kafka.ssl.key-store-type=PKCS12
            spring.kafka.ssl.key-store-location=file:/run/secrets/kafka-backend.p12
            spring.kafka.ssl.key-store-password=%s
            spring.kafka.ssl.key-password=%s
            spring.kafka.ssl.trust-store-type=PKCS12
            spring.kafka.ssl.trust-store-location=file:/run/secrets/kafka-truststore.p12
            spring.kafka.ssl.trust-store-password=%s
            spring.kafka.properties.ssl.endpoint.identification.algorithm=https
            spring.kafka.properties.ssl.enabled.protocols=TLSv1.2,TLSv1.3
            spring.kafka.producer.acks=all
            spring.kafka.producer.properties.enable.idempotence=true
            """.formatted(backend,backend,trust));
        try(var files=Files.list(out)) { for(Path path:files.toList()) ownerOnly(path,false); }
        System.out.println("Generated Kafka PKI and configuration in "+out+". Leaf certificates expire in 365 days. Keep the CA offline.");
    }
    private static String identity(String name,String dn,boolean server) throws Exception {
        String password=password(name);
        tool("-genkeypair","-alias",name,"-keyalg","RSA","-keysize","3072","-dname",dn,"-validity","365","-storetype","PKCS12",
                "-keystore",file(name+".p12"),"-storepass:file",file(name+".password"));
        tool("-certreq","-alias",name,"-keystore",file(name+".p12"),"-storepass:file",file(name+".password"),"-file",file(name+".csr"));
        var args=new ArrayList<>(List.of("-gencert","-rfc","-alias","ca","-keystore",file("ca.p12"),"-storepass:file",file("ca.password"),
                "-infile",file(name+".csr"),"-outfile",file(name+".crt"),"-validity","365","-ext","KU=digitalSignature,keyEncipherment",
                "-ext",server?"EKU=serverAuth,clientAuth":"EKU=clientAuth"));
        if(server) args.addAll(List.of("-ext","SAN=dns:kafka"));
        tool(args.toArray(String[]::new));
        tool("-importcert","-noprompt","-alias","ca","-keystore",file(name+".p12"),"-storepass:file",file(name+".password"),"-file",file("ca.crt"));
        tool("-importcert","-noprompt","-alias",name,"-keystore",file(name+".p12"),"-storepass:file",file(name+".password"),"-file",file(name+".crt"));
        return password;
    }
    private static String client(String identity,String password,String trust) {
        return """
            security.protocol=SSL
            ssl.enabled.protocols=TLSv1.2,TLSv1.3
            ssl.keystore.type=PKCS12
            ssl.keystore.location=/run/secrets/kafka-%s.p12
            ssl.keystore.password=%s
            ssl.key.password=%s
            ssl.truststore.type=PKCS12
            ssl.truststore.location=/run/secrets/kafka-truststore.p12
            ssl.truststore.password=%s
            ssl.endpoint.identification.algorithm=https
            request.timeout.ms=5000
            default.api.timeout.ms=10000
            max.block.ms=10000
            delivery.timeout.ms=15000
            """.formatted(identity,password,password,trust);
    }
    private static String password(String name) throws Exception {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String password=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        write(name+".password",password+"\n"); return password;
    }
    private static String file(String name) { return out.resolve(name).toString(); }
    private static void write(String name,String value) throws Exception {
        Files.writeString(out.resolve(name),value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        ownerOnly(out.resolve(name),false);
    }
    private static void ownerOnly(Path path,boolean directory) throws Exception {
        if(Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
        }
    }
    private static void tool(String... arguments) throws Exception {
        var command=new ArrayList<String>(); command.add(KEYTOOL); command.addAll(List.of(arguments));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if(process.waitFor()!=0) throw new IllegalStateException("keytool failed: "+output);
    }
}
