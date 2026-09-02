package com.adpoint.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.res.Configuration;

import com.google.android.gms.ads.*;
import com.google.android.gms.ads.rewarded.*;
import com.google.fireputBooleanbase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    // Google test rewarded ad. Replace with your approved production ID before release.
    private static final String AD_UNIT_ID = "ca-app-pub-6915600224604560/8485757967";

    private static final int DAILY_LIMIT = 50;
    private static final int POINTS_PER_AD = 50;

    private SharedPreferences sp;
    private RewardedAd rewardedAd;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private LinearLayout content;
    private TextView pointsText, adsText, statusText;
    private Button watchButton;

    private int points, adsToday;
    private boolean darkMode() { return sp != null && sp.getBoolean("darkMode", false); }
    private int pageBg() { return darkMode() ? Color.rgb(18, 20, 28) : Color.rgb(248, 249, 252); }
    private int pageText() { return darkMode() ? Color.rgb(235, 235, 240) : Color.DKGRAY; }

    int dp(float x) {
        return (int) (x * getResources().getDisplayMetrics().density + .5f);
    }

    TextView tv(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        return t;
    }

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    void add(String s, int size, int color) {
        content.addView(tv(s, size, color));
    }


    String hashPassword(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            return value;
        }
    }

    void validateLocalState() {
        int storedPoints = sp.getInt("points", 0);
        int storedAds = sp.getInt("ads", 0);

        boolean repaired = false;
        if (storedPoints < 0) {
            storedPoints = 0;
            repaired = true;
        }
        if (storedAds < 0 || storedAds > DAILY_LIMIT) {
            storedAds = Math.max(0, Math.min(DAILY_LIMIT, storedAds));
            repaired = true;
        }

        if (repaired) {
            sp.edit().putInt("points", storedPoints).putInt("ads", storedAds).apply();
            addNotification("Local data was validated and repaired.");
        }
    }

    void addNotification(String message) {
        if (!sp.getBoolean("notifications", true)) return;

        String raw = sp.getString("notifications_log", "");
        String entry = now() + "|" + message;
        raw = raw.isEmpty() ? entry : entry + "\n" + raw;

        String[] lines = raw.split("\n");
        StringBuilder keep = new StringBuilder();
        for (int i = 0; i < lines.length && i < 30; i++) {
            if (i > 0) keep.append("\n");
            keep.append(lines[i]);
        }

        sp.edit().putString("notifications_log", keep.toString()).apply();
    }

    void notificationCenter() {
        base(Color.rgb(38, 45, 70), pageBg());
        add("Notifications", 26, pageText());

        String raw = sp.getString("notifications_log", "");
        if (raw.isEmpty()) {
            add("No notifications yet.", 16, pageText());
        } else {
            for (String line : raw.split("\n")) {
                String[] p = line.split("\\|", 2);
                String when = p.length > 0 ? p[0] : "";
                String message = p.length > 1 ? p[1] : line;
                add("• " + message + "\n  " + when, 15, pageText());
            }
        }

        Button clear = new Button(this);
        clear.setText("Clear Notifications");
        clear.setAllCaps(false);
        content.addView(clear);
        clear.setOnClickListener(v -> {
            sp.edit().remove("notifications_log").apply();
            notificationCenter();
        });
    }

    void productionReadinessPage() {
        base(Color.rgb(35, 40, 65), pageBg());
        add("Production Readiness", 26, pageText());
        add("App-side features: Ready for continued testing", 17, Color.rgb(0, 120, 90));
        add("Cloud account/backend: Not connected", 16, pageText());
        add("Server-side points verification: Not connected", 16, pageText());
        add("Automatic reward fulfillment: Not connected", 16, pageText());
        add("Production AdMob IDs: Not configured", 16, pageText());
        add("Play Store legal/listing review: Still required", 16, pageText());
        add("This page is a checklist only. It does not claim these external services are active.", 14, Color.GRAY);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        sp = getSharedPreferences("adpoint", MODE_PRIVATE);
        resetDailyCountIfNeeded();
        validateLocalState();

        points = sp.getInt("points", 0);
        adsToday = sp.getInt("ads", 0);

        MobileAds.initialize(this, status -> {});

        // VERIFIED STARTUP FLOW:
        // SplashActivity always opens this activity, and this activity always opens
        // the Login/Create Account screen first. Home can only be reached after login.
        buildLogin();
    }


    void buildLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(15, 18, 35));

        TextView title = tv("ADPOINT", 32, Color.WHITE);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = tv("Login or create a new account to continue", 16, Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        EditText user = new EditText(this);
        user.setHint("Username");
        user.setTextColor(Color.WHITE);
        user.setHintTextColor(Color.LTGRAY);
        user.setSingleLine(true);
        user.setText(sp.getString("username", ""));
        root.addView(user, new LinearLayout.LayoutParams(-1, dp(58)));

        EditText pass = new EditText(this);
        pass.setHint("Password");
        pass.setTextColor(Color.WHITE);
        pass.setHintTextColor(Color.LTGRAY);
        pass.setSingleLine(true);
        pass.setInputType(0x81);
        root.addView(pass, new LinearLayout.LayoutParams(-1, dp(58)));

        Button login = new Button(this);
        login.setText("Login"); login.setAllCaps(false);
        root.addView(login, new LinearLayout.LayoutParams(-1, dp(56)));
        
login.setOnClickListener(v -> {
    String u = user.getText().toString().trim();
    String pw = pass.getText().toString();

    if (u.isEmpty()) {
        toast("Enter your email.");
        return;
    }

    if (pw.isEmpty()) {
        toast("Enter your password.");
        return;
    }

    mAuth.signInWithEmailAndPassword(u, pw)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    sp.edit()
                            .putString("username", u)
                            .putBoolean("loggedIn", true)
                            .putInt("loginFailures", 0)
                            .remove("loginLockedUntil")
                            .apply();

                    addNotification("Login successful.");
                    buildHome();
                    loadAd();
                } else {
                    toast("Login failed: " + task.getException().getMessage());
                }
            });
});

        Button signup = new Button(this);
        signup.setText("Create Account"); signup.setAllCaps(false);
        root.addView(signup, new LinearLayout.LayoutParams(-1, dp(56)));
        signup.setOnClickListener(v -> buildSignup());
        setContentView(root);
    }

    void buildSignup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(15, 18, 35));
        TextView title=tv("Create AdPoint Account",28,Color.WHITE); title.setTypeface(null,1); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView note=tv("This is a local account for the current development build.",14,Color.LTGRAY); note.setGravity(Gravity.CENTER); root.addView(note);
        EditText user=new EditText(this); user.setHint("Enter your email"); user.setSingleLine(true); root.addView(user,new LinearLayout.LayoutParams(-1,dp(58)));
        EditText pass=new EditText(this); pass.setHint("Choose password (min 4 characters)"); pass.setSingleLine(true); pass.setInputType(0x81); root.addView(pass,new LinearLayout.LayoutParams(-1,dp(58)));
        Button create=new Button(this); create.setText("Create Account"); create.setAllCaps(false); root.addView(create,new LinearLayout.LayoutParams(-1,dp(56)));
        create.setOnClickListener(v -> {
            String u=user.getText().toString().trim(), pw=pass.getText().toString();
            if (u.isEmpty()) { toast("Enter your email."); return; }
            if (pw.length()<4) { toast("Password must be at least 4 characters."); return; }
           mAuth.createUserWithEmailAndPassword(u, pw)
                         .addOnCompleteListener(task -> {
                                 if (task.isSuccessful()) {
                                    sp.edit()
                                                 .putString("username", u)
                                                 .putBoolean("onboardingDone", true)
                                                 .putBoolean("loggedIn", true)
                                                .putInt("loginFailures", 0)
                                                .apply();
 
                    addNotification("Account created successfully.");
                    toast("Account created successfully.");
                    buildHome();
                    loadAd();
                } else {
                         toast("Account creation failed: " + task.getException().getMessage());
     }
        });
        Button back=new Button(this); back.setText("Back to Login"); back.setAllCaps(false); root.addView(back); back.setOnClickListener(v->buildLogin());
        setContentView(root);
    }

    void resetDailyCountIfNeeded() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(sp.getString("day", ""))) {
            sp.edit().putString("day", today).putInt("ads", 0).apply();
        }
    }

    boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    void base(int head, int bg) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(darkMode() ? Color.rgb(18, 20, 28) : bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(12), 0);
        header.setBackgroundColor(head);

        TextView h = tv("AdPoint", 25, Color.WHITE);
        h.setTypeface(null, 1);
        header.addView(h, new LinearLayout.LayoutParams(0, -1, 1));

        TextView subtitle = tv("Earn • Track • Redeem", 12, Color.WHITE);
        subtitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(subtitle);

        root.addView(header, new LinearLayout.LayoutParams(-1, dp(76)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(darkMode() ? Color.rgb(28, 30, 40) : Color.WHITE);

        String[] labels = {"⌂\nHome", "▶\nWatch", "▣\nWallet", "🎁\nRedeem", "●\nProfile"};

        for (String x : labels) {
            Button b = new Button(this);
            b.setText(x);
            b.setTextSize(10);
            b.setAllCaps(false);
            b.setPadding(0, 0, 0, 0);

            nav.addView(b, new LinearLayout.LayoutParams(0, dp(64), 1));

            if (x.contains("Home")) b.setOnClickListener(v -> buildHome());
            else if (x.contains("Watch")) b.setOnClickListener(v -> buildWatch());
            else if (x.contains("Wallet")) b.setOnClickListener(v -> buildWallet());
            else if (x.contains("Redeem")) b.setOnClickListener(v -> buildRedeem());
            else b.setOnClickListener(v -> buildProfile());
        }

        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(68)));
        setContentView(root);
    }

    void card(String title, String body, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(rounded(darkMode() ? Color.rgb(32, 35, 46) : Color.WHITE, 14));

        TextView a = tv(title, 18, accent);
        a.setTypeface(null, 1);
        box.addView(a);

        box.addView(tv(body, 14, Color.DKGRAY));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(12), dp(8), dp(12), dp(4));
        content.addView(box, p);
    }


    void buildOnboarding() {
        base(Color.rgb(13, 43, 120), Color.rgb(246, 248, 255));

        add("Welcome to AdPoint", 28, Color.rgb(20, 35, 80));
        add("A simple reward-tracking app", 17, Color.DKGRAY);

        card("1. Watch", "When a rewarded ad is available, complete it to earn points.", Color.rgb(25, 84, 165));
        card("2. Track", "Your completed rewards and redeem requests appear in History.", Color.rgb(0, 121, 107));
        card("3. Redeem", "Submit a reward request when you have enough points.", Color.rgb(220, 20, 20));

        add("Important: this development build uses Google's test rewarded ads and demo redeem requests.", 14, Color.DKGRAY);

        Button start = new Button(this);
        start.setText("Get Started");
        start.setAllCaps(false);
        content.addView(start, new LinearLayout.LayoutParams(-1, dp(56)));
        start.setOnClickListener(v -> {
            sp.edit().putBoolean("onboardingDone", true).putBoolean("loggedIn", false).apply();
            buildLogin();
        });
    }

    void buildHome() {
        base(Color.rgb(25, 84, 165), pageBg());

        add("Welcome back!", 14, Color.GRAY);

        pointsText = tv(points + " points", 36, Color.rgb(20, 30, 65));
        pointsText.setTypeface(null, 1);
        content.addView(pointsText);

        adsText = tv("Today's Ads: " + adsToday + " / " + DAILY_LIMIT, 16, Color.DKGRAY);
        content.addView(adsText);

        card("Earn points", "Complete a rewarded ad and receive " + POINTS_PER_AD + " points.", Color.rgb(25, 84, 165));
        card("Track activity", "Your ad rewards and redeem requests are stored in History.", Color.rgb(0, 121, 107));
        card("Redeem rewards", "Choose from ₹30, ₹50, ₹80 or ₹159 demo reward requests.", Color.rgb(220, 20, 20));

        Button b = new Button(this);
        b.setText("▶ Go to Watch & Earn");
        b.setAllCaps(false);
        content.addView(b, new LinearLayout.LayoutParams(-1, dp(56)));
        b.setOnClickListener(v -> buildWatch());

        if (!isOnline()) {
            add("⚠ You're offline. The app works locally, but new ads need an internet connection.", 14, Color.rgb(190, 80, 0));
        }
    }

    void buildWatch() {
        base(Color.rgb(24, 20, 48), Color.rgb(10, 12, 28));

        add("Watch & Earn", 26, Color.WHITE);

        pointsText = tv(points + " points", 32, Color.rgb(225, 215, 255));
        pointsText.setTypeface(null, 1);
        content.addView(pointsText);

        adsText = tv("Today's Ads: " + adsToday + " / " + DAILY_LIMIT, 16, Color.LTGRAY);
        content.addView(adsText);

        add("Complete a rewarded ad to receive " + POINTS_PER_AD + " points.", 15, Color.LTGRAY);

        watchButton = new Button(this);
        watchButton.setText("▶ Watch Ad & Earn " + POINTS_PER_AD + " Points");
        watchButton.setAllCaps(false);
        content.addView(watchButton, new LinearLayout.LayoutParams(-1, dp(60)));
        watchButton.setOnClickListener(v -> watchAd());

        Button retry = new Button(this);
        retry.setText("Retry loading ad");
        retry.setAllCaps(false);
        content.addView(retry);
        retry.setOnClickListener(v -> loadAd());

        statusText = tv("", 14, Color.LTGRAY);
        content.addView(statusText);

        add("Daily limit: 50 ads. Rewards are added only after the rewarded-ad SDK confirms completion.", 14, Color.LTGRAY);

        update();
    }

    void buildWallet() {
        base(Color.rgb(0, 121, 107), pageBg());

        add("Wallet", 26, Color.rgb(20, 55, 55));
        add(points + " points available", 30, Color.rgb(20, 55, 55));
        add("Total earned: " + sp.getInt("earned", 0) + " points", 16, Color.DKGRAY);
        add("Total redeemed: " + sp.getInt("redeemed", 0) + " points", 16, Color.DKGRAY);

        Button h = new Button(this);
        h.setText("View Full History");
        h.setAllCaps(false);
        content.addView(h);
        h.setOnClickListener(v -> history());
    }

    int need(int amount) {
        return amount * 100;
    }

    void redeemCard(int amount) {
        int cost = need(amount);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(rounded(darkMode() ? Color.rgb(32, 35, 46) : Color.WHITE, 14));

        TextView title = tv("▶  Google Play Reward", 20, Color.rgb(35, 80, 180));
        title.setTypeface(null, 1);
        row.addView(title);

        row.addView(tv("₹" + amount + " • " + cost + " points required", 15, Color.DKGRAY));

        Button b = new Button(this);
        b.setText("Redeem ₹" + amount);
        b.setAllCaps(false);
        row.addView(b);
        b.setOnClickListener(v -> redeem(amount, cost));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(12), dp(8), dp(12), dp(2));
        content.addView(row, p);
    }

    void buildRedeem() {
        base(Color.rgb(36, 45, 65), pageBg());

        add("Redeem", 26, Color.rgb(25, 30, 40));
        add("Google Play Rewards", 16, Color.GRAY);
        add("Redeem requests are demo requests in this build. A production app needs a real fulfillment process.", 14, Color.DKGRAY);

        Button status = new Button(this);
        status.setText("Check Redeem Status");
        status.setAllCaps(false);
        content.addView(status);
        status.setOnClickListener(v -> redeemStatusPage());

        redeemCard(30);
        redeemCard(50);
        redeemCard(80);
        redeemCard(159);

        add("Balance: " + points + " points", 17, pageText());
        String last = sp.getString("lastRedeem", "");
        if (!last.isEmpty()) add("Latest redeem: " + last + " • Pending", 14, pageText());
    }


    String newRequestId() {
        return "AP-" + System.currentTimeMillis();
    }

    void saveRedeemRequest(String id, int amount, int cost) {
        sp.edit()
                .putString("redeem_" + id,
                        id + "|" + amount + "|" + cost + "|Pending|" + now())
                .putString("lastRedeemRequestId", id)
                .apply();
    }

    void redeemStatusPage() {
        base(Color.rgb(36, 45, 65), pageBg());
        add("Redeem Status", 26, pageText());

        String id = sp.getString("lastRedeemRequestId", "");
        if (id.isEmpty()) {
            add("No redeem request yet.", 16, pageText());
        } else {
            String raw = sp.getString("redeem_" + id, "");
            String[] p = raw.split("\\|", 5);
            if (p.length >= 5) {
                add("Request ID: " + p[0], 16, pageText());
                add("Reward: ₹" + p[1], 18, pageText());
                add("Points reserved: " + p[2], 16, pageText());
                add("Status: " + p[3], 18, Color.rgb(220, 120, 0));
                add("Submitted: " + p[4], 14, Color.GRAY);
                add("Workflow: Pending → Approved/Rejected → Completed", 15, pageText());
                add("Only a real backend/admin system should change a production request status.", 14, Color.GRAY);
            }
        }

        Button back = new Button(this);
        back.setText("Back to Redeem");
        back.setAllCaps(false);
        content.addView(back);
        back.setOnClickListener(v -> buildRedeem());
    }

    void reportProblemPage() {
        base(Color.rgb(220, 20, 20), pageBg());
        add("Report a Problem", 26, pageText());

        EditText input = new EditText(this);
        input.setHint("Describe the problem");
        input.setMinLines(4);
        input.setTextColor(pageText());
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));

        Button submit = new Button(this);
        submit.setText("Save Report");
        submit.setAllCaps(false);
        content.addView(submit);
        submit.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) {
                toast("Please describe the problem.");
                return;
            }
            String id = "SUP-" + System.currentTimeMillis();
            sp.edit().putString("support_" + id, now() + "|" + message + "|Open").apply();
            addHistory("Support report submitted", 0);
            addNotification("Support report saved locally: " + id);
            toast("Report saved locally. A real support backend is needed for delivery.");
            buildProfile();
        });
    }

    void redeem(int amount, int cost) {
        long lastTime = sp.getLong("lastRedeemTime_" + amount, 0);
        if (System.currentTimeMillis() - lastTime < 24L * 60L * 60L * 1000L) {
            toast("A request for this amount was already submitted recently. Please wait before submitting again.");
            return;
        }

        if (points < cost) {
            toast("You need " + (cost - points) + " more points for ₹" + amount);
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Redeem")
                .setMessage("Redeem ₹" + amount + " using " + cost + " points?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (d, w) -> {
                    points -= cost;
                    int totalRedeemed = sp.getInt("redeemed", 0) + cost;
                    String requestId = newRequestId();

                    sp.edit()
                            .putInt("points", points)
                            .putInt("redeemed", totalRedeemed)
                            .putString("lastRedeem", "₹" + amount + " Google Play reward")
                            .putLong("lastRedeemTime_" + amount, System.currentTimeMillis())
                            .apply();

                    saveRedeemRequest(requestId, amount, cost);
                    addHistory("Redeem request • ₹" + amount + " • Pending • " + requestId, -cost);
                    addNotification("Redeem request submitted: ₹" + amount + " (" + requestId + ").");
                    toast("Redeem request saved with ID " + requestId);
                    buildRedeem();
                })
                .show();
    }

    String now() {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date());
    }

    void addHistory(String title, int pointChange) {
        String raw = sp.getString("history", "");
        String entry = now() + "|" + title + "|" + pointChange;

        if (raw.isEmpty()) raw = entry;
        else raw = entry + "\n" + raw;

        // Keep recent history compact.
        String[] lines = raw.split("\n");
        StringBuilder keep = new StringBuilder();
        for (int i = 0; i < lines.length && i < (sp.getBoolean("compactHistory", false) ? 30 : 100); i++) {
            if (i > 0) keep.append("\n");
            keep.append(lines[i]);
        }

        sp.edit().putString("history", keep.toString()).apply();
    }

    void history() {
        base(Color.rgb(220, 20, 20), pageBg());

        add("History", 26, Color.DKGRAY);
        add("Recent activity", 15, Color.GRAY);

        String raw = sp.getString("history", "");
        if (raw.isEmpty()) {
            add("No activity yet. Completed rewarded ads and redeem requests will appear here.", 16, Color.DKGRAY);
        } else {
            String[] lines = raw.split("\n");
            for (String line : lines) {
                String[] parts = line.split("\\|", 3);
                String date = parts.length > 0 ? parts[0] : "";
                String title = parts.length > 1 ? parts[1] : line;
                String change = parts.length > 2 ? parts[2] : "";

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(14), dp(8), dp(14), dp(8));
                item.setBackground(rounded(Color.rgb(250, 250, 250), 10));

                TextView t = tv(title, 16, Color.DKGRAY);
                t.setPadding(0, 0, 0, 0);
                item.addView(t);

                TextView meta = tv(date + (change.startsWith("-") ? " • " + change + " points" : " • +" + change + " points"), 12, Color.GRAY);
                meta.setPadding(0, 0, 0, 0);
                item.addView(meta);

                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                p.setMargins(dp(12), dp(5), dp(12), dp(3));
                content.addView(item, p);
            }
        }
    }

    void info(String title, String msg) {
        base(Color.rgb(220, 20, 20), pageBg());
        add(title, 26, Color.DKGRAY);
        add(msg, 16, Color.DKGRAY);

        Button b = new Button(this);
        b.setText("Back to Profile");
        b.setAllCaps(false);
        content.addView(b);
        b.setOnClickListener(v -> buildProfile());
    }

    void row(String icon, String title, String sub, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(8), dp(8), dp(8));

        TextView i = tv(icon, 23, Color.DKGRAY);
        r.addView(i, new LinearLayout.LayoutParams(dp(48), -2));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = tv(title, 17, Color.DKGRAY);
        titleView.setTypeface(null, 1);
        titleView.setPadding(0, 0, 0, 0);
        texts.addView(titleView);

        if (!sub.isEmpty()) {
            TextView subView = tv(sub, 13, Color.GRAY);
            subView.setPadding(0, 0, 0, 0);
            texts.addView(subView);
        }

        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = tv("›", 28, Color.GRAY);
        r.addView(arrow);

        r.setOnClickListener(l);
        content.addView(r);
    }

    void buildProfile() {
        base(Color.rgb(220, 20, 20), pageBg());

        add("Profile", 26, Color.DKGRAY);

        String name = sp.getString("username", "AdPoint User");
        add("●   " + name + "\n     Manage your AdPoint account", 20, Color.DKGRAY);

        row("👤", "Your Account", "Name and points overview", v -> accountPage());
        row("↪", "Logout", "Sign out from this device", v -> { sp.edit().putBoolean("loggedIn", false).apply(); buildLogin(); });
        row("◷", "History", "Watch and redeem activity", v -> history());
        row("?", "Help & Support", "FAQ and troubleshooting", v -> helpPage());
        row("▤", "Terms & Conditions", "Read app rules", v -> termsPage());
        row("⌑", "Privacy Policy", "How app data is handled", v -> privacyPage());
        row("⚙", "Settings", "App preferences", v -> settingsPage());
        row("🔔", "Notifications", "Local reward and account updates", v -> notificationCenter());
        row("✓", "Production Readiness", "See what still needs external setup", v -> productionReadinessPage());
        row("ⓘ", "About", "About AdPoint", v -> aboutPage());
    }


    void settingsPage() {
        base(Color.rgb(220, 20, 20), pageBg());
        add("Settings", 26, pageText());

        Switch theme = new Switch(this);
        theme.setText("Dark gaming theme");
        theme.setTextSize(16);
        theme.setChecked(darkMode());
        theme.setOnCheckedChangeListener((button, checked) -> {
            sp.edit().putBoolean("darkMode", checked).apply();
            settingsPage();
        });
        content.addView(theme);

        Switch notifications = new Switch(this);
        notifications.setText("Reward notifications");
        notifications.setTextSize(16);
        notifications.setChecked(sp.getBoolean("notifications", true));
        notifications.setOnCheckedChangeListener((button, checked) ->
                sp.edit().putBoolean("notifications", checked).apply());
        content.addView(notifications);

        Switch compact = new Switch(this);
        compact.setText("Compact activity history");
        compact.setTextSize(16);
        compact.setChecked(sp.getBoolean("compactHistory", false));
        compact.setOnCheckedChangeListener((button, checked) ->
                sp.edit().putBoolean("compactHistory", checked).apply());
        content.addView(compact);

        Button replay = new Button(this);
        replay.setText("Show welcome guide again");
        replay.setAllCaps(false);
        content.addView(replay);
        replay.setOnClickListener(v -> {
            sp.edit().putBoolean("onboardingDone", false).apply();
            buildOnboarding();
        });

        Button reset = new Button(this);
        reset.setText("Clear local activity history");
        reset.setAllCaps(false);
        content.addView(reset);
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Clear History?")
                .setMessage("This clears local activity history only. Your points balance will not be changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d,w) -> {
                    sp.edit().remove("history").apply();
                    toast("Local history cleared.");
                }).show());

        Button clearAll = new Button(this);
        clearAll.setText("Reset local app data");
        clearAll.setAllCaps(false);
        content.addView(clearAll);
        clearAll.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Reset local data?")
                .setMessage("This will remove locally stored points, history and preferences. It cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d,w) -> {
                    sp.edit().clear().apply();
                    points = 0;
                    adsToday = 0;
                    toast("Local app data reset.");
                    buildOnboarding();
                }).show());

        add("These settings are stored on this device in the current development build.", 14, Color.GRAY);
    }

    void accountPage() {
        base(Color.rgb(220, 20, 20), pageBg());

        add("Your Account", 26, Color.DKGRAY);

        String name = sp.getString("username", "AdPoint User");
        add("Name: " + name, 19, Color.DKGRAY);
        add("Current balance: " + points + " points", 17, Color.DKGRAY);
        add("Total earned: " + sp.getInt("earned", 0) + " points", 17, Color.DKGRAY);
        add("Today's ads: " + adsToday + " / " + DAILY_LIMIT, 17, Color.DKGRAY);
        add("Account type: Local development account", 15, pageText());
        add("Login protection: temporary lock after repeated wrong passwords", 14, pageText());

        Button edit = new Button(this);
        edit.setText("Edit display name");
        edit.setAllCaps(false);
        content.addView(edit);
        edit.setOnClickListener(v -> editName());
    }

    void editName() {
        final EditText input = new EditText(this);
        input.setHint("Enter display name");
        input.setText(sp.getString("username", "AdPoint User"));

        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit display name")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Name cannot be empty.");
                    } else {
                        sp.edit().putString("username", name).apply();
                        accountPage();
                    }
                })
                .show();
    }

    void helpPage() {
        base(Color.rgb(220, 20, 20), pageBg());

        add("Help & Support", 26, Color.DKGRAY);
        add("Frequently asked questions", 19, Color.DKGRAY);

        add("Why didn't I receive points?\nPoints are added only when the rewarded-ad SDK reports a completed reward.", 15, Color.DKGRAY);
        add("Why is an ad unavailable?\nCheck your internet connection and use Retry loading ad. Ad availability also depends on ad inventory.", 15, Color.DKGRAY);
        add("How does redeem work?\nThis build records local demo redeem requests. A real reward delivery system still requires a backend or manual approval process.", 15, pageText());
        add("Why is my balance local?\nThis version does not yet use a cloud account, so uninstalling or resetting app data can remove locally stored information.", 15, pageText());
        add("Can I watch unlimited ads?\nNo. The current daily limit is 50 rewarded ads.", 15, pageText());

        Button report = new Button(this);
        report.setText("Report a Problem");
        report.setAllCaps(false);
        content.addView(report);
        report.setOnClickListener(v -> reportProblemPage());
    }

    void termsPage() {
        info("Terms & Conditions",
                "• Use the app responsibly.\n\n" +
                "• Points are awarded only after a valid rewarded-ad completion.\n\n" +
                "• Fraudulent, automated or abusive activity may be rejected.\n\n" +
                "• Redeem requests in this development build are local demo requests unless a real fulfillment system is added.\n\n• External services, rewards and production availability are subject to their own rules and approval.");
    }

    void privacyPage() {
        info("Privacy Policy",
                "This development build stores points, activity history and a display name locally on the device.\n\n" +
                "Rewarded ads are provided by Google Mobile Ads.\n\n" +
                "Before public release, publish a complete privacy policy that accurately describes all data collection, sharing and retention. Do not claim that the app is production-ready until those details are reviewed.");
    }

    void aboutPage() {
        info("About AdPoint",
                "Version 1.0\n\n" +
                "Features: Watch, Wallet, Redeem, History and Profile.\n\n" +
                "Daily ad limit: 50.\n\n" +
                "Development builds use Google's test rewarded-ad unit.");
    }

    void loadAd() {
        if (!isOnline()) {
            rewardedAd = null;
            if (statusText != null) statusText.setText("No internet connection. Connect to the internet and retry.");
            return;
        }

        if (statusText != null) statusText.setText("Loading rewarded ad…");

        RewardedAd.load(
                this,
                AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                        update();
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError e) {
                        rewardedAd = null;
                        if (statusText != null) {
                            statusText.setText("Ad unavailable right now. Try again shortly.");
                        }
                    }
                }
        );
    }

    void watchAd() {
        if (adsToday >= DAILY_LIMIT) {
            toast("Today's 50-ad limit reached.");
            return;
        }

        if (!isOnline()) {
            toast("Internet connection is required to load a new ad.");
            update();
            return;
        }

        if (rewardedAd == null) {
            toast("Ad is loading. Please try again shortly.");
            loadAd();
            return;
        }

        RewardedAd ad = rewardedAd;
        rewardedAd = null;

        if (watchButton != null) watchButton.setEnabled(false);

        ad.show(this, rewardItem -> {
            long lastReward = sp.getLong("lastRewardAt", 0);
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastReward < 1500) {
                toast("Duplicate reward protection blocked a repeated callback.");
                loadAd();
                update();
                return;
            }
            points += POINTS_PER_AD;
            adsToday++;

            sp.edit()
                    .putInt("points", points)
                    .putInt("ads", adsToday)
                    .putInt("earned", sp.getInt("earned", 0) + POINTS_PER_AD)
                    .putLong("lastRewardAt", nowMs)
                    .apply();

            addHistory("Rewarded ad completed", POINTS_PER_AD);

            addNotification("Reward received: +" + POINTS_PER_AD + " points.");
            toast("+" + POINTS_PER_AD + " points added!");
            loadAd();
            update();
        });
    }

    void update() {
        if (pointsText != null) pointsText.setText(points + " points");
        if (adsText != null) adsText.setText("Today's Ads: " + adsToday + " / " + DAILY_LIMIT);

        if (watchButton != null) {
            boolean ready = rewardedAd != null && adsToday < DAILY_LIMIT;
            watchButton.setEnabled(ready);

            if (statusText != null) {
                if (adsToday >= DAILY_LIMIT) {
                    statusText.setText("Daily limit reached. Come back tomorrow.");
                } else if (!isOnline()) {
                    statusText.setText("No internet connection. Ads require internet.");
                } else if (rewardedAd == null) {
                    statusText.setText("Loading rewarded ad…");
                } else {
                    statusText.setText("Rewarded ad ready • " + adsToday + "/" + DAILY_LIMIT + " today");
                }
            }
        }
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
