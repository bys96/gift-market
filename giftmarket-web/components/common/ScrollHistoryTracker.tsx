"use client";

import { useEffect } from "react";
import { startScrollHistoryTracking } from "@/lib/scroll-restoration";

export default function ScrollHistoryTracker() {
  useEffect(() => startScrollHistoryTracking(), []);
  return null;
}
