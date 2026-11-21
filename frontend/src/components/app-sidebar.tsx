"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Files, LayoutDashboard, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import styles from "./app-sidebar.module.scss";

export default function AppSidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const isDashboard = pathname === "/";
  const isHotels = pathname === "/hotels" || pathname.startsWith("/hotels/");

  return (
    <aside className={`${styles.sidebar} ${collapsed ? styles.collapsed : ""}`}>
      <div className={styles.sidebarHeader}>
        <Link className={styles.brand} href="/" aria-label="Azzurro dashboard">
          <span className={styles.brandMark} aria-hidden="true">
            <Image
              className={styles.brandLogo}
              src="/azzurro-mark.webp"
              alt=""
              width={38}
              height={38}
              sizes="38px"
            />
          </span>
          <span className={styles.brandCopy}>
            <strong>Azzurro</strong>
            <span>Guest experience</span>
          </span>
        </Link>
        <button
          className={styles.collapseButton}
          type="button"
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          aria-controls="main-navigation"
          aria-expanded={!collapsed}
          onClick={() => setCollapsed((current) => !current)}
        >
          {collapsed ? <PanelLeftOpen size={17} aria-hidden="true" /> : <PanelLeftClose size={17} aria-hidden="true" />}
        </button>
      </div>

      <nav id="main-navigation" className={styles.navigation} aria-label="Main navigation">
        <p className={styles.navigationLabel}>Workspace</p>
        <Link
          className={`${styles.navigationLink} ${isDashboard ? styles.activeLink : ""}`}
          href="/"
          aria-label="Dashboard"
          title="Dashboard"
          aria-current={isDashboard ? "page" : undefined}
        >
          <LayoutDashboard size={18} strokeWidth={1.7} aria-hidden="true" />
          <span>Dashboard</span>
        </Link>
        <Link
          className={`${styles.navigationLink} ${isHotels ? styles.activeLink : ""}`}
          href="/hotels"
          aria-label="Hotels"
          title="Hotels"
          aria-current={isHotels ? "page" : undefined}
        >
          <Files size={18} strokeWidth={1.7} aria-hidden="true" />
          <span>Hotels</span>
        </Link>
      </nav>

    </aside>
  );
}
