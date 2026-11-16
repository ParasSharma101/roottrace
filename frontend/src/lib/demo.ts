// Demo controls are opt-in and never shown by production builds.
export const isDemoEnabled =
  process.env.NODE_ENV === 'development' &&
  process.env.NEXT_PUBLIC_DEMO_ENABLED === 'true';
