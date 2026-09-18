package com.adpoint.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.PorterDuffXfermode;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    // Google test rewarded ad. Replace with your approved production ID before release.
    private static final String AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

    private static final int DAILY_LIMIT = 50;
    private static final int POINTS_PER_AD = 50;

    private SharedPreferences sp;
    private RewardedAd rewardedAd;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private LinearLayout content;
    private TextView pointsText, adsText, statusText;
    private Button watchButton;
    private ProgressBar adProgress;
    private String currentPage = "";
    private final ArrayDeque<String> pageHistory = new ArrayDeque<>();
    private boolean suppressHistory = false;

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

    void actionButton(String label, View.OnClickListener listener) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    b.setBackground(rounded(Color.rgb(70, 65, 220), 18));

    LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(56));
    p.setMargins(dp(12), dp(6), dp(12), dp(6));

    content.addView(b, p);
    b.setOnClickListener(listener);
}
    View banner(int imageRes) {
    ImageView image = new ImageView(this);
    image.setImageResource(imageRes);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);

    LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(180));
    p.setMargins(dp(12), dp(8), dp(12), dp(8));

    image.setLayoutParams(p);
    return image;
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
   void resetDailyCountIfNeeded() {
    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    String savedDay = sp.getString("day", "");

    if (!today.equals(savedDay)) {
        sp.edit()
                .putString("day", today)
                .putInt("ads", 0)
                .apply();
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
        enterPage("notifications");
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
        enterPage("production");
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

        try {
    mAuth = FirebaseAuth.getInstance();
    db = FirebaseFirestore.getInstance();
} catch (Exception e) {
    mAuth = null;
    db = null;
}

        sp = getSharedPreferences("adpoint", MODE_PRIVATE);
        resetDailyCountIfNeeded();
        validateLocalState();

        points = sp.getInt("points", 0);
        adsToday = sp.getInt("ads", 0);

        MobileAds.initialize(this, status -> {});

        // Keep the Firebase session across app restarts. Login is required again only
        // after logout/delete, or if the Firebase session has expired.
        FirebaseUser existingUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (existingUser != null && sp.getBoolean("loggedIn", false) && existingUser.isEmailVerified()) {
            clearPageHistory();
            buildHome();
            loadAd();
        } else {
            if (existingUser != null && !existingUser.isEmailVerified()) {
                mAuth.signOut();
            }
            sp.edit().putBoolean("loggedIn", false).apply();
            clearPageHistory();
            buildLogin();
        }
    }


    void buildLogin() {
        currentPage = "login";
        LinearLayout root = authRoot();

        TextView back = authBackButton(false);
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView title = tv("Sign In", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView welcome = tv("Welcome Back", 20, Color.WHITE); welcome.setGravity(Gravity.CENTER); root.addView(welcome);
        TextView sub = tv("Log in to your AdPoint account", 14, Color.GRAY); sub.setGravity(Gravity.CENTER); root.addView(sub);

        EditText user = authField("✉", "Enter Your Gmail", false);
        user.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        user.setText(sp.getString("username", ""));
        root.addView(user);

        EditText pass = authField("🔒", "Enter Your Password", true);
        root.addView(pass);

        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox remember = new CheckBox(this);
        remember.setText("Remember me"); remember.setTextColor(Color.WHITE); remember.setTextSize(14);
        remember.setChecked(true);
        options.addView(remember, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView forgot = tv("Forgot Password", 14, Color.MAGENTA); forgot.setGravity(Gravity.CENTER);
        options.addView(forgot, new LinearLayout.LayoutParams(dp(125), dp(42)));
        root.addView(options);
        forgot.setOnClickListener(v -> forgotPasswordPage());

        Button login = gradientButton("Sign In");
        root.addView(login);
        login.setOnClickListener(v -> {
            String u = user.getText().toString().trim(), pw = pass.getText().toString();
            if (!isGmailFormat(u)) { toast("Enter a valid Gmail address."); return; }
            if (pw.isEmpty()) { toast("Enter your password."); return; }
            showAuthLoading(root, true);
            mAuth.signInWithEmailAndPassword(u, pw).addOnCompleteListener(task -> {
                showAuthLoading(root, false);
                if (task.isSuccessful()) {
                    FirebaseUser fu = mAuth.getCurrentUser();
                    if (fu != null && !fu.isEmailVerified()) {
                        fu.sendEmailVerification();
                        mAuth.signOut();
                        sp.edit().putBoolean("loggedIn", false).apply();
                        toast("Verify your Gmail first. A verification link was sent.");
                        buildLogin();
                        return;
                    }
                    sp.edit().putString("username", u).putBoolean("loggedIn", true)
                            .putBoolean("rememberMe", remember.isChecked()).putInt("loginFailures", 0)
                            .remove("loginLockedUntil").apply();
                    addNotification("Login successful.");
                    clearPageHistory(); buildHome(); loadAd();
                } else {
                    String msg = task.getException() != null ? task.getException().getMessage() : "Login failed";
                    toast("Login failed: " + msg);
                }
            });
        });

        addAuthDivider(root, "or");
        root.addView(socialButton("f   Continue With Facebook", v -> toast("Facebook login is not configured yet.")));
        root.addView(socialButton("G   Continue With Google", v -> toast("Google login is not configured yet.")));
        root.addView(socialButton("   Continue With Apple", v -> toast("Apple login is not configured yet.")));

        TextView signup = tv("Don't have an account?  Sign up", 14, Color.WHITE);
        signup.setGravity(Gravity.CENTER);
        root.addView(signup, new LinearLayout.LayoutParams(-1, dp(46)));
        signup.setOnClickListener(v -> { clearPageHistory(); buildSignup(); });
        setAuthContentView(root);
    }

    void buildSignup() {
        currentPage = "signup";
        LinearLayout root = authRoot();

        TextView back = authBackButton(true);
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(38)));
        back.setOnClickListener(v -> { clearPageHistory(); mAuth.signOut(); buildLogin(); });

        TextView title = tv("Sign Up", 30, Color.WHITE); title.setTypeface(Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView welcome = tv("Create Your Account", 20, Color.WHITE); welcome.setGravity(Gravity.CENTER); root.addView(welcome);
        TextView sub = tv("Join AdPoint to start your journey", 14, Color.GRAY); sub.setGravity(Gravity.CENTER); root.addView(sub);

        EditText name = authField("👤", "Enter Your Name", false); root.addView(name);
        EditText user = authField("✉", "Enter Your Gmail", false);
        user.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); root.addView(user);
        EditText pass = authField("🔒", "Enter Your Password", true); root.addView(pass);
        EditText confirm = authField("🔒", "Confirm Your Password", true); root.addView(confirm);

        CheckBox terms = new CheckBox(this); terms.setText("I agree to Terms and condition"); terms.setTextColor(Color.WHITE); terms.setTextSize(14); root.addView(terms);

        Button create = gradientButton("Sign In"); root.addView(create);
        create.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String u = user.getText().toString().trim();
            String pw = pass.getText().toString();
            String cp = confirm.getText().toString();
            if (n.isEmpty()) { toast("Enter your name."); return; }
            if (!isGmailFormat(u)) { toast("Enter a valid Gmail address."); return; }
            if (pw.length() < 6) { toast("Password must be at least 6 characters."); return; }
            if (!pw.equals(cp)) { toast("Passwords do not match."); return; }
            if (!terms.isChecked()) { toast("Please agree to the Terms and condition."); return; }
            showAuthLoading(root, true);
            mAuth.createUserWithEmailAndPassword(u, pw).addOnCompleteListener(task -> {
                showAuthLoading(root, false);
                if (task.isSuccessful()) {
                    FirebaseUser fu = mAuth.getCurrentUser();
                    if (fu != null) {
                        fu.sendEmailVerification();
                    }
                    sp.edit().putString("username", u).putBoolean("onboardingDone", true)
                            .putBoolean("loggedIn", false).putString("displayName", n).putInt("loginFailures", 0).apply();
                    mAuth.signOut();
                    addNotification("Account created. Verify your Gmail before signing in.");
                    toast("Verification link sent to your Gmail.");
                    buildLogin();
                } else {
                    String msg = task.getException() != null ? task.getException().getMessage() : "Account creation failed";
                    toast("Account creation failed: " + msg);
                }
            });
        });

        addAuthDivider(root, "or");
        root.addView(socialButton("f", v -> toast("Facebook login is not configured yet.")));
        root.addView(socialButton("G", v -> toast("Google login is not configured yet.")));
        root.addView(socialButton("", v -> toast("Apple login is not configured yet.")));

        TextView login = tv("Already have an Account?  Login", 14, Color.WHITE); login.setGravity(Gravity.CENTER); root.addView(login, new LinearLayout.LayoutParams(-1, dp(46)));
        login.setOnClickListener(v -> { clearPageHistory(); buildLogin(); });
        setAuthContentView(root);
    }

    LinearLayout authRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        root.setBackgroundColor(Color.rgb(5, 7, 20));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        return root;
    }

    void setAuthContentView(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(5, 7, 20));
        scroll.addView(root);
        setContentView(scroll);
    }

    TextView authBackButton(boolean active) {
        TextView b = tv("‹", 34, Color.WHITE);
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        b.setOnClickListener(v -> buildLogin());
        return b;
    }

    EditText authField(String icon, String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(icon + "   " + hint); e.setHintTextColor(Color.rgb(205, 190, 220)); e.setTextColor(Color.WHITE);
        e.setSingleLine(true); e.setTextSize(16); e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(rounded(Color.rgb(45, 24, 55), 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54)); p.setMargins(0, dp(5), 0, dp(5)); e.setLayoutParams(p);
        if (password) {
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            e.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_view, 0);
            e.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP && event.getX() > e.getWidth() - dp(55)) {
                    int pos = e.getSelectionStart();
                    boolean visible = (e.getInputType() & android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0;
                    e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (visible ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
                    e.setSelection(Math.max(0, pos));
                    return true;
                }
                return false;
            });
        }
        return e;
    }

    Button gradientButton(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(16);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.MAGENTA, Color.rgb(65,110,255)}); g.setCornerRadius(dp(28)); b.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54)); p.setMargins(0, dp(8), 0, dp(8)); b.setLayoutParams(p); return b;
    }

    Button socialButton(String label, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setBackground(rounded(Color.rgb(45,25,58), 26)); b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50)); p.setMargins(0, dp(4), 0, dp(4)); b.setLayoutParams(p); return b;
    }

    void addAuthDivider(LinearLayout root, String text) {
        TextView d = tv("────────  " + text + "  ────────", 14, Color.GRAY); d.setGravity(Gravity.CENTER); root.addView(d, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    void showAuthLoading(LinearLayout root, boolean show) {
        // A small inline spinner makes network/auth waits visible instead of leaving a blank screen.
        if (show) {
            ProgressBar p = new ProgressBar(this); p.setIndeterminate(true); p.setTag("auth_spinner"); root.addView(p, 0, new LinearLayout.LayoutParams(dp(28), dp(28)));
        } else {
            View old = root.findViewWithTag("auth_spinner"); if (old != null) root.removeView(old);
        }
    }

    boolean isGmailFormat(String email) {
        return email.matches("^[A-Za-z0-9._%+-]+@gmail\\.com$");
    }

    void forgotPasswordPage() {
        LinearLayout root = authRoot();
        TextView title = tv("Forgot Password", 26, Color.WHITE); title.setTypeface(Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); root.addView(title);
        EditText email = authField("✉", "Enter Your Gmail", false); email.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); root.addView(email);
        Button send = gradientButton("Send Reset Link"); root.addView(send);
        send.setOnClickListener(v -> {
            String u = email.getText().toString().trim();
            if (!isGmailFormat(u)) { toast("Enter a valid Gmail address."); return; }
            showAuthLoading(root, true);
            mAuth.sendPasswordResetEmail(u).addOnCompleteListener(t -> { showAuthLoading(root, false); toast(t.isSuccessful() ? "Reset link sent to your Gmail." : "Could not send reset link."); if (t.isSuccessful()) buildLogin(); });
        });
        Button back = socialButton("Back to Login", v -> buildLogin()); root.addView(back);
        setAuthContentView(root);
    }

    void enterPage(String page) {
        if (page == null) return;
        if (currentPage.isEmpty()) { currentPage = page; return; }
        if (!page.equals(currentPage) && !suppressHistory) pageHistory.push(currentPage);
        currentPage = page;
    }

    void clearPageHistory() { pageHistory.clear(); currentPage = ""; }

    void goBack() {
        if (pageHistory.isEmpty()) { buildHome(); return; }
        String page = pageHistory.pop();
        suppressHistory = true;
        switch (page) {
            case "watch": buildWatch(); break;
            case "referral": referralPage(); break;
            case "leaderboard": leaderboardPage(); break;
            case "scratch": scratchPage(); break;
            case "wallet": buildWallet(); break;
            case "redeem": buildRedeem(); break;
            case "redeemStatus": redeemStatusPage(); break;
            case "history": history(); break;
            case "profile": buildProfile(); break;
            case "settings": settingsPage(); break;
            case "account": accountPage(); break;
            case "help": helpPage(); break;
            case "terms": termsPage(); break;
            case "privacy": privacyPage(); break;
            case "about": aboutPage(); break;
            default: buildHome(); break;
        }
        suppressHistory = false;
    }

    @Override
    public void onBackPressed() {
        if ("login".equals(currentPage)) {
            super.onBackPressed();
            return;
        }
        if ("signup".equals(currentPage)) {
            clearPageHistory();
            buildLogin();
            return;
        }
        goBack();
    }

    void base(int head, int bg) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9, 11, 31));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (!"home".equals(currentPage)) {
            Button back = new Button(this); back.setText("‹"); back.setTextSize(28); back.setAllCaps(false); back.setTextColor(Color.WHITE); back.setBackgroundColor(Color.TRANSPARENT);
            back.setOnClickListener(v -> goBack());
            header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(58)));
        }
        header.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(28, 22, 90), Color.rgb(15, 100, 155)});
        header.setBackground(hg);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.adpoint.app.R.drawable.adpoint_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(58)));

        TextView h = tv("AdPoint", 24, Color.WHITE);
        h.setTypeface(null, 1);
        header.addView(h, new LinearLayout.LayoutParams(0, -1, 1));

        TextView subtitle = tv("Earn • Play • Redeem", 11, Color.WHITE);
        subtitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(68)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(8));

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER); nav.setBackgroundColor(Color.rgb(18,20,45));
        String[] labels = {"⌂\nHome", "▶\nWatch", "▣\nWallet", "🎁\nRedeem", "🎟\nRefer", "●\nProfile"};
        for (String x : labels) {
            Button b = new Button(this); b.setText(x); b.setTextSize(10); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setPadding(0,0,0,0);
            nav.addView(b, new LinearLayout.LayoutParams(dp(78), dp(64)));
            if (x.contains("Home")) b.setOnClickListener(v -> buildHome());
            else if (x.contains("Watch")) b.setOnClickListener(v -> buildWatch());
            else if (x.contains("Wallet")) b.setOnClickListener(v -> buildWallet());
            else if (x.contains("Redeem")) b.setOnClickListener(v -> buildRedeem());
            else if (x.contains("Refer")) b.setOnClickListener(v -> referralPage());
            else b.setOnClickListener(v -> buildProfile());
        }
        navScroll.addView(nav);
        root.addView(navScroll, new LinearLayout.LayoutParams(-1, dp(66)));
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
    private boolean isOnline() {
    ConnectivityManager cm =
            (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

    if (cm == null) return false;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        NetworkCapabilities nc =
                cm.getNetworkCapabilities(cm.getActiveNetwork());

        return nc != null &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    return false;
}
    void buildHome() {
        enterPage("home");
        base(Color.rgb(30, 40, 100), pageBg());
        content.addView(banner(R.drawable.adpoint_home_banner));
        add("Welcome back!", 14, Color.rgb(180,190,230));
        TextView pts=tv(points+" POINTS",34,Color.WHITE); pts.setTypeface(null,1); content.addView(pts);
        add("Today's Ads: "+adsToday+" / "+DAILY_LIMIT,15,Color.LTGRAY);
        card("⚡ Watch & Earn","Complete rewarded ads and get "+POINTS_PER_AD+" points.",Color.rgb(90,210,255));
        card("🎁 Redeem","Google Play rewards • ₹30 • ₹50 • ₹80 • ₹159",Color.rgb(110,235,190));
        actionButton("▶  Watch Ads",v->buildWatch());
        actionButton("🎁  Redeem Rewards",v->buildRedeem());
        actionButton("🎟  Referral & Bonus",v->referralPage());
        actionButton("🏆  Leaderboard",v->leaderboardPage());
        actionButton("🎁  Scratch & Win",v->scratchPage());
        if(!isOnline()) add("📡 Offline — internet is required for ads.",14,Color.rgb(255,180,80));
    }

    void buildWatch() {
        enterPage("watch");
        base(Color.rgb(24,20,70),Color.rgb(10,12,28));
        content.addView(banner(R.drawable.adpoint_watch_banner));
        add("Your balance: "+points+" points",18,Color.WHITE);
        add("Today's ads: "+adsToday+" / "+DAILY_LIMIT,14,Color.LTGRAY);
        watchButton=new Button(this); watchButton.setText("▶  WATCH AD  +50 POINTS"); watchButton.setAllCaps(false);
        watchButton.setTextColor(Color.WHITE); watchButton.setBackground(rounded(Color.rgb(75,65,220),18));
        content.addView(watchButton,new LinearLayout.LayoutParams(-1,dp(60))); watchButton.setOnClickListener(v->watchAd());
        Button retry=new Button(this); retry.setText("↻  Reload Ad"); retry.setAllCaps(false); content.addView(retry); retry.setOnClickListener(v->loadAd());
        LinearLayout adStatusRow = new LinearLayout(this); adStatusRow.setGravity(Gravity.CENTER_VERTICAL);
        adProgress = new ProgressBar(this); adProgress.setIndeterminate(true); adStatusRow.addView(adProgress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        statusText=tv("",14,Color.LTGRAY); statusText.setPadding(dp(8),dp(10),dp(8),dp(10)); adStatusRow.addView(statusText, new LinearLayout.LayoutParams(0,-2,1)); content.addView(adStatusRow);
        add("Maximum 50 rewarded ads per day.",14,Color.LTGRAY);
        update();
    }


    void referralPage() {
        enterPage("referral");
        base(Color.rgb(35,35,95),Color.rgb(9,11,31));
        add("🎟 Referral Program",28,Color.WHITE);
        String code=sp.getString("refCode","");
        if(code.isEmpty()){ code="AP"+Math.abs((sp.getString("username","USER").hashCode()%900000+100000)); sp.edit().putString("refCode",code).apply(); }
        final String referralCode = code;
        add("Your referral code",15,Color.LTGRAY);
        TextView c=tv(referralCode,32,Color.rgb(100,230,255)); c.setTypeface(null,1); c.setGravity(Gravity.CENTER); content.addView(c);
        actionButton("Copy Referral Code",v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("AdPoint Referral",referralCode));toast("Referral code copied.");});
        EditText enter=new EditText(this); enter.setHint("Enter a friend's referral code"); enter.setHintTextColor(Color.LTGRAY); enter.setTextColor(Color.WHITE); enter.setSingleLine(true);
        content.addView(enter,new LinearLayout.LayoutParams(-1,dp(54)));
        actionButton("Apply Referral Code",v->{String x=enter.getText().toString().trim(); if(x.isEmpty()){toast("Enter a referral code.");return;} sp.edit().putString("referredBy",x).apply(); toast("Referral code saved.");});
        add("Share your code with friends. Referral rewards will be connected to the production backend before launch.", 14, Color.LTGRAY);
    }

    void leaderboardPage() {
        enterPage("leaderboard");
        base(Color.rgb(45,25,95),Color.rgb(9,11,31));
        add("🏆 Leaderboard",28,Color.WHITE);
        add("Top AdPoint earners",15,Color.LTGRAY);
        add("🥇  1. Top Player     — 12,500 pts",18,Color.WHITE);
        add("🥈  2. Reward Master — 10,800 pts",18,Color.WHITE);
        add("🥉  3. Point Hunter  — 9,650 pts",18,Color.WHITE);
        add("Your rank: —",18,Color.rgb(100,230,255));
        add("Leaderboard shown as demo data until the production backend is connected.",13,Color.LTGRAY);
    }

    void scratchPage() {
        enterPage("scratch");
        base(Color.rgb(35,20,75),Color.rgb(8,10,28));
        content.addView(banner(R.drawable.adpoint_scratch_banner));
        add("Scratch the card to reveal your reward!",18,Color.WHITE);
        add("You can win 10–50 points. Maximum reward: 50 points.",14,Color.LTGRAY);
        final ScratchCardView card=new ScratchCardView(this);
        content.addView(card,new LinearLayout.LayoutParams(-1,dp(230)));
        TextView state=tv("Scratch with your finger ✨",16,Color.rgb(150,225,255)); state.setGravity(Gravity.CENTER); content.addView(state);
        card.onComplete=()->{
            int reward=10+(new Random().nextInt(5)*10); // 10,20,30,40,50 only
            points+=reward; sp.edit().putInt("points",points).putInt("earned",sp.getInt("earned",0)+reward).apply();
            state.setText("You won "+reward+" points!");
            addNotification("Scratch reward: "+reward+" points.");
            new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Watch a short ad?")
                    .setMessage("The ad is optional and gives no extra points. Your scratch reward is already added.")
                    .setNegativeButton("Skip", null)
                    .setPositiveButton("Watch Ad", (d,w) -> showAdWithoutPoints()).show();
        };
        actionButton("Back to Home",v->buildHome());
    }

    class ScratchCardView extends View {
        Paint cover=new Paint(3), clear=new Paint(3), textPaint=new Paint(3);
        ArrayList<Path> scratches=new ArrayList<>();
        Path active; int scratched=0; boolean done=false; Runnable onComplete;
        ScratchCardView(Context c){
            super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            cover.setColor(Color.rgb(145,150,165)); clear.setStyle(Paint.Style.STROKE);
            clear.setStrokeWidth(dp(34)); clear.setStrokeCap(Paint.Cap.ROUND);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        protected void onDraw(Canvas c){
            c.saveLayer(0,0,getWidth(),getHeight(),null);
            GradientDrawable bg=rounded(Color.rgb(48,25,105),22); bg.setBounds(0,0,getWidth(),getHeight()); bg.draw(c);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(30));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("🎁  50 POINTS MAX",getWidth()/2f,getHeight()/2f, textPaint);
            textPaint.setTextSize(dp(16));
            textPaint.setTypeface(Typeface.DEFAULT);
            c.drawText("Scratch to reveal your reward",getWidth()/2f,getHeight()/2f+dp(35),textPaint);
            cover.setColor(Color.rgb(165,170,185));
            c.drawRoundRect(new RectF(dp(12),dp(12),getWidth()-dp(12),getHeight()-dp(12)),dp(20),dp(20),cover);
            textPaint.setColor(Color.rgb(70,75,90));
            textPaint.setTextSize(dp(24));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD); 
            c.drawText("SCRATCH HERE",getWidth()/2f,getHeight()/2f, textPaint);
            for(Path p:scratches)c.drawPath(p,clear);
            c.restore();
        }
        public boolean onTouchEvent(android.view.MotionEvent e){
            if(done)return true;
            if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){
                active=new Path(); active.moveTo(e.getX(),e.getY()); scratches.add(active); invalidate(); return true;
            }
            if(e.getAction()==android.view.MotionEvent.ACTION_MOVE && active!=null){
                active.lineTo(e.getX(),e.getY()); scratched++; invalidate();
                if(scratched>=65){done=true; if(onComplete!=null)onComplete.run();}
                return true;
            }
            return true;
        }
    }

    void buildWallet() {
        enterPage("wallet");
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
        enterPage("redeem");
        base(Color.rgb(55,30,90),pageBg());
        content.addView(banner(R.drawable.adpoint_redeem_banner));
        add("Google Play Rewards",24,Color.WHITE);
        add("100 points = ₹1",14,Color.LTGRAY);
        redeemCard(30); redeemCard(50); redeemCard(80); redeemCard(159);
        add("Balance: "+points+" points",17,Color.WHITE);
        actionButton("Check Redeem Status",v->redeemStatusPage());
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
        enterPage("redeemStatus");
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
        enterPage("report");
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
        enterPage("history");
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
        enterPage("profile");
        base(Color.rgb(220, 20, 20), pageBg());

        add("Profile", 26, Color.DKGRAY);

        String name = sp.getString("displayName", sp.getString("username", "AdPoint User"));
        add("●   " + name + "\n     " + sp.getString("username", "") + "\n     Manage your AdPoint account", 18, Color.DKGRAY);

        row("👤", "Your Account", "Name and points overview", v -> accountPage());
        row("↪", "Logout", "Sign out from this device", v -> { if (mAuth != null) mAuth.signOut(); sp.edit().putBoolean("loggedIn", false).apply(); clearPageHistory(); buildLogin(); });
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
        enterPage("settings");
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
        enterPage("account");
        base(Color.rgb(220, 20, 20), pageBg());

        add("Your Account", 26, Color.DKGRAY);

        String name = sp.getString("displayName", "AdPoint User");
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

        Button delete = new Button(this);
        delete.setText("Delete Account"); delete.setAllCaps(false); delete.setTextColor(Color.WHITE); delete.setBackground(rounded(Color.rgb(170, 35, 55), 18));
        content.addView(delete);
        delete.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Delete AdPoint Account")
                .setMessage("This will sign you out and delete your Firebase account if allowed. This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d,w) -> deleteFirebaseAccount()).show());
    }

    void deleteFirebaseAccount() {
        FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
        if (user == null) { sp.edit().clear().apply(); clearPageHistory(); buildLogin(); return; }
        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                sp.edit().clear().apply(); points = 0; adsToday = 0; clearPageHistory(); toast("Account deleted."); buildLogin();
            } else {
                toast("Account deletion requires a recent login. Please log in again and retry.");
            }
        });
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
                        sp.edit().putString("displayName", name).apply();
                        accountPage();
                    }
                })
                .show();
    }

    void helpPage() {
        enterPage("help");
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
        enterPage("terms");
        info("Terms & Conditions",
                "• Use the app responsibly.\n\n" +
                "• Points are awarded only after a valid rewarded-ad completion.\n\n" +
                "• Fraudulent, automated or abusive activity may be rejected.\n\n" +
                "• Redeem requests in this development build are local demo requests unless a real fulfillment system is added.\n\n• External services, rewards and production availability are subject to their own rules and approval.");
    }

    void privacyPage() {
        enterPage("privacy");
        info("Privacy Policy",
                "This development build stores points, activity history and a display name locally on the device.\n\n" +
                "Rewarded ads are provided by Google Mobile Ads.\n\n" +
                "Before public release, publish a complete privacy policy that accurately describes all data collection, sharing and retention. Do not claim that the app is production-ready until those details are reviewed.");
    }

    void aboutPage() {
        enterPage("about");
        info("About AdPoint",
                "Version 1.0\n\n" +
                "Features: Watch, Wallet, Redeem, History and Profile.\n\n" +
                "Daily ad limit: 50.\n\n" +
                "Development builds use Google's test rewarded-ad unit.");
    }

    void loadAd() {
        if (!isOnline()) {
            rewardedAd = null;
            if (adProgress != null) adProgress.setVisibility(View.GONE);
            if (statusText != null) statusText.setText("No internet connection. Connect to the internet and retry.");
            return;
        }

        if (adProgress != null) adProgress.setVisibility(View.VISIBLE);
        if (statusText != null) statusText.setText("Loading rewarded ad…");

        RewardedAd.load(
                this,
                AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedAd = ad;
                        if (adProgress != null) adProgress.setVisibility(View.GONE);
                        update();
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError e) {
                        rewardedAd = null;
                        if (adProgress != null) adProgress.setVisibility(View.GONE);
                        if (statusText != null) {
                            statusText.setText("Ad error: " + e.getMessage());
                        }
                    }
                }
        );
    }

    void showAdWithoutPoints() {
        if (!isOnline()) { toast("Internet connection is required for the ad."); return; }
        if (rewardedAd == null) { toast("Ad is still loading. Please try again shortly."); loadAd(); return; }
        RewardedAd ad = rewardedAd; rewardedAd = null;
        ad.show(this, rewardItem -> {
            toast("Ad completed. No extra points were added.");
            loadAd();
        });
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
