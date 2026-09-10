package th.ac.vu.classflow.data.bootstrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;

public final class CurriculumCatalog {

    private CurriculumCatalog() {
    }

    public static List<SeedTrack> initialTracks() {
        return Collections.unmodifiableList(Arrays.asList(ss1(), ss2()));
    }

    public static String weekId(int weekNumber) {
        return String.format(java.util.Locale.US, "W%02d", weekNumber);
    }

    private static SeedTrack ss1() {
        Track track = new Track(
                "ss1",
                "SS1",
                "Python Foundations",
                "พื้นฐานการเขียนโปรแกรมด้วย Python",
                10,
                true
        );
        List<CurriculumWeek> weeks = Arrays.asList(
                week(1, "Python Basics", "print(), input(), variables, data types, type casting, and basic mathematics.", "FOUNDATION", "LEARNING"),
                week(2, "Decision Making 1", "if, elif, else, comparison operators, and indentation.", "FOUNDATION", "LEARNING"),
                week(3, "Decision Making 2", "Logical operators, nested if statements, and modulo.", "FOUNDATION", "LEARNING"),
                week(4, "While Loop", "while, break, continue, and assignment operators.", "FOUNDATION", "LEARNING"),
                week(5, "For Loop", "for, in, range(), and loop iteration.", "FOUNDATION", "LEARNING"),
                week(6, "List", "Create and access lists, append(), remove(), len(), and slicing.", "FOUNDATION", "LEARNING"),
                week(7, "Dictionary", "Key-value data and access with keys(), values(), and items().", "FOUNDATION", "LEARNING"),
                week(8, "Function", "Create and call functions, parameters, arguments, return, and task decomposition.", "FOUNDATION", "LEARNING"),
                week(9, "Capstone Assembly", "Combine prior knowledge to plan, design, build, and test an MVP.", "CAPSTONE", "PROJECT"),
                week(10, "Demo Day", "Present the project and explain code structure and flow through a code walkthrough.", "DEMO", "DEMO")
        );
        return new SeedTrack(track, weeks);
    }

    private static SeedTrack ss2() {
        Track track = new Track(
                "ss2",
                "SS2",
                "Smart Systems",
                "Software + Hardware Integration",
                10,
                true
        );
        List<CurriculumWeek> weeks = Arrays.asList(
                week(1, "Remember & Save", "File I/O, TXT, saving and restoring system status, reboot behavior, and OLED status.", "DATA_DECISION", "INTEGRATION"),
                week(2, "Record the Real World", "CSV, timestamps, sensor readings, history, and time-series thinking.", "DATA_DECISION", "INTEGRATION"),
                week(3, "Structured Data", "JSON and structured multi-sensor data combined into a system state.", "DATA_DECISION", "INTEGRATION"),
                week(4, "Data to Decision", "Rules, thresholds, data-driven decisions, and actuator responses.", "DATA_DECISION", "INTEGRATION"),
                week(5, "Network Basics", "Serial-to-Wi-Fi communication, message formats, and connected-device concepts.", "CONNECTED_DEVICE", "INTEGRATION"),
                week(6, "Build an App", "Streamlit controls, status UI, and controlling hardware through an app.", "APP_CLOUD", "INTEGRATION"),
                week(7, "Data & Dashboard", "Timestamps, graphs, current versus history, dashboards, and data history.", "APP_CLOUD", "INTEGRATION"),
                week(8, "Cloud Connect", "Firebase concepts and sending data to and receiving data from the cloud.", "APP_CLOUD", "INTEGRATION"),
                week(9, "Smart System Integration", "Combine software, hardware, networking, and cloud into a complete smart system.", "SMART_SYSTEM_INTEGRATION", "PROJECT"),
                week(10, "Demo Day", "Demonstrate the system and explain its code and architecture through a walkthrough.", "DEMO", "DEMO")
        );
        return new SeedTrack(track, weeks);
    }

    private static CurriculumWeek week(int number, String title, String summary,
                                       String phase, String progressType) {
        return new CurriculumWeek(weekId(number), number, title, summary, phase, progressType);
    }

    public static final class SeedTrack {
        private final Track track;
        private final List<CurriculumWeek> weeks;

        private SeedTrack(Track track, List<CurriculumWeek> weeks) {
            this.track = track;
            this.weeks = Collections.unmodifiableList(weeks);
        }

        public Track getTrack() {
            return track;
        }

        public List<CurriculumWeek> getWeeks() {
            return weeks;
        }
    }
}
