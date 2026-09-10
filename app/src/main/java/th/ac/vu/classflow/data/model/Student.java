package th.ac.vu.classflow.data.model;

import com.google.firebase.Timestamp;

public final class Student {

    private String studentId;
    private String name;
    private String nickname;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Student() {
        // Required by Firestore.
    }

    public Student(String studentId, String name, String nickname, boolean active) {
        this.studentId = studentId;
        this.name = name;
        this.nickname = nickname;
        this.active = active;
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
