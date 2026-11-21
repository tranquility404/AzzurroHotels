import type { Metadata } from "next";
import { Suspense } from "react";
import { Geist, Geist_Mono } from "next/font/google";
import AppSidebar from "@/components/app-sidebar";
import sidebarStyles from "@/components/app-sidebar.module.scss";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Azzurro | Guest experience",
  description: "Hotel performance, guest reviews and property details in one workspace.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      data-theme="coastal"
    >
      <body className="min-h-screen flex flex-col lg:flex-row">
        <Suspense fallback={<div className={sidebarStyles.sidebarFallback} aria-hidden="true" />}>
          <AppSidebar />
        </Suspense>
        <div className="min-w-0 flex-1">{children}</div>
      </body>
    </html>
  );
}