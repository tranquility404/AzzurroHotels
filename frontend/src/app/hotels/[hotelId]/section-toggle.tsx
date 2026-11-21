"use client";

import { ChevronDown } from "lucide-react";
import styles from "./page.module.scss";

export default function SectionToggle({
  label,
  controls,
  expanded,
  onToggle,
}: {
  label: string;
  controls: string;
  expanded: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      className={styles.sectionToggleButton}
      type="button"
      aria-label={`${expanded ? "Collapse" : "Expand"} ${label}`}
      aria-expanded={expanded}
      aria-controls={controls}
      onClick={onToggle}
    >
      <ChevronDown size={17} aria-hidden="true" />
    </button>
  );
}
