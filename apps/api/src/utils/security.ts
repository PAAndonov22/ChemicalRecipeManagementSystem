import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { env } from "../config/env.js";

export type TokenPayload = {
  userId: number;
  role: "Admin" | "Chemist" | "Technician";
  email: string;
};

const SALT_ROUNDS = 10;

export const hashPassword = async (plainText: string): Promise<string> => bcrypt.hash(plainText, SALT_ROUNDS);

export const comparePassword = async (plainText: string, hash: string): Promise<boolean> => bcrypt.compare(plainText, hash);

export const signAccessToken = (payload: TokenPayload): string =>
  jwt.sign(payload, env.jwtSecret, { expiresIn: env.jwtExpiresIn as jwt.SignOptions["expiresIn"] });

export const verifyAccessToken = (token: string): TokenPayload =>
  jwt.verify(token, env.jwtSecret) as TokenPayload;
