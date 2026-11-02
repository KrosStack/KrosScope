package sample.tasktide.sync.service;

public class Report implements android.os.Parcelable {
    public int count;
    public java.lang.String title;
    public java.util.List<java.lang.String> tags;
    public android.os.Bundle extras;
    public sample.tasktide.sync.service.SyncStatus[] history;
    public static final int STATE_OPEN = 0;
    public static final android.os.Parcelable.Creator<Report> CREATOR = null;
}
