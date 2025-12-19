package com.example.earthspirit.cravings;

import java.util.HashMap;
import java.util.Map;

public class DailyRequest {
    public long date; // Epoch Day
    public String grade; // C, B, A, S
    public Map<Integer, TaskItem> items = new HashMap<>();
    public boolean rewardsClaimed;

    public static class TaskItem {
        public String key; // Custom Item Key or Material Name
        public int amount;
        public boolean submitted;
        
        public TaskItem() {}
        
        public TaskItem(String key, int amount) {
            this.key = key;
            this.amount = amount;
            this.submitted = false;
        }
    }
}
