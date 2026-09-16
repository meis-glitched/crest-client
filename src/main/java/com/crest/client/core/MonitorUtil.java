package com.crest.client.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MonitorUtil {
    private MonitorUtil() {}

    // Returns the currently focused monitor name (Hyprland), falling back to the
    // first monitor reported by gpu-screen-recorder. null if none found.
    public static String getFocusedMonitor() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "hyprctl monitors -j | jq -r '.[] | select(.focused == true) | .name'"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String name = r.readLine();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {}
        // fallback: first monitor from gpu-screen-recorder list
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"gpu-screen-recorder", "--list-monitors"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null) {
                    int sep = line.indexOf('|');
                    if (sep > 0) return line.substring(0, sep);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Width/height of the currently focused monitor, or {0,0} if unknown.
    public static int[] getFocusedMonitorSize() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "hyprctl monitors -j | jq -r '.[] | select(.focused == true) | \"\\(.width)x\\(.height)\"'"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null) {
                    int x = line.indexOf('x');
                    if (x > 0) {
                        return new int[]{Integer.parseInt(line.substring(0, x)), Integer.parseInt(line.substring(x + 1))};
                    }
                }
            }
        } catch (Exception ignored) {}
        // fallback: first monitor from gpu-screen-recorder list (name|WxH)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"gpu-screen-recorder", "--list-monitors"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null) {
                    int sep = line.indexOf('|');
                    if (sep > 0) {
                        String res = line.substring(sep + 1);
                        int x = res.indexOf('x');
                        if (x > 0) {
                            return new int[]{Integer.parseInt(res.substring(0, x)), Integer.parseInt(res.substring(x + 1))};
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    // Refresh rate (Hz) of the focused monitor, or 0 if unknown.
    public static int getFocusedMonitorRefresh() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "hyprctl monitors -j | jq -r '.[] | select(.focused == true) | .refreshRate'"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isEmpty()) {
                    return Math.max(1, (int) Math.round(Double.parseDouble(line)));
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}