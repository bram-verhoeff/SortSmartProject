import "@sorte/ui/fonts";
import "@sorte/ui/tokens.css";
import "./styles.css";
import { StrictMode, useEffect } from "react";
import { createRoot } from "react-dom/client";
import { Navigate, createBrowserRouter, RouterProvider } from "react-router-dom";
import { Layout } from "./App";
import { Docs } from "./pages/Docs";
import { Home } from "./pages/Home";
import { NotFound } from "./pages/NotFound";

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: "/", element: <Home /> },
      { path: "/docs", element: <Docs /> },
      // Older addresses keep working. The app is its own page, so leave the router for it.
      { path: "/download", element: <OpenApp /> },
      { path: "/sorting", element: <Navigate to="/#streams" replace /> },
      { path: "/technology", element: <Navigate to="/" replace /> },
      { path: "/settings", element: <Navigate to="/docs" replace /> },
      { path: "*", element: <NotFound /> },
    ],
  },
]);

function OpenApp() {
  useEffect(() => window.location.replace("/app/"), []);
  return null;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
);
