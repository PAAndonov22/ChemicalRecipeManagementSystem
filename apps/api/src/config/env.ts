import dotenv from "dotenv";

dotenv.config();

const toNumber = (value: string | undefined, fallback: number): number => {
  if (!value) return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export const env = {
  port: toNumber(process.env.PORT, 4000),
  jwtSecret: process.env.JWT_SECRET ?? "change-me-super-secret-for-production",
  jwtExpiresIn: process.env.JWT_EXPIRES_IN ?? "8h"
};
