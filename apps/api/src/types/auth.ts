export type Role = "Admin" | "Chemist" | "Technician";

export type AuthenticatedUser = {
  id: number;
  email: string;
  role: Role;
  fullName: string;
};
