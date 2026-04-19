import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from "react";
import { apiClient } from "../api/client";
import type { User } from "../api/types";

type AuthContextValue = {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
  updateLocalUser: (user: User) => void;
};

const TOKEN_KEY = "crms_token";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider = ({ children }: PropsWithChildren) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem(TOKEN_KEY));
  const [isLoading, setIsLoading] = useState(true);

  const applyToken = useCallback((nextToken: string | null) => {
    setToken(nextToken);
    apiClient.setToken(nextToken);

    if (nextToken) {
      localStorage.setItem(TOKEN_KEY, nextToken);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  }, []);

  const logout = useCallback(() => {
    applyToken(null);
    setUser(null);
  }, [applyToken]);

  const refreshProfile = useCallback(async () => {
    if (!token) {
      setUser(null);
      return;
    }

    const response = await apiClient.me();
    setUser(response.user);
  }, [token]);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await apiClient.login(email, password);
      applyToken(response.token);
      setUser(response.user);
    },
    [applyToken]
  );

  useEffect(() => {
    apiClient.setToken(token);

    const initialize = async () => {
      if (!token) {
        setIsLoading(false);
        return;
      }

      try {
        const response = await apiClient.me();
        setUser(response.user);
      } catch {
        logout();
      } finally {
        setIsLoading(false);
      }
    };

    void initialize();
  }, [logout, token]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isLoading,
      login,
      logout,
      refreshProfile,
      updateLocalUser: setUser
    }),
    [isLoading, login, logout, refreshProfile, token, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return context;
};
