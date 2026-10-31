//----------------------------------------------------------------------
// This file is part of 'kpclientd-android'.
// Copyright (C) 2026-present, Bernd Fix   >Y<
//
// 'kpclientd-android' is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License,
// or (at your option) any later version.
//
// 'kpclientd-android' is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: AGPL3.0-or-later
//----------------------------------------------------------------------

package org.hoi_polloi.kpdaemon;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.hoi_polloi.kpdaemon.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MainActivity is the only activity in this app.
public class MainActivity extends AppCompatActivity {

    // binding to layout resource
    private ActivityMainBinding binding;

    // Attributes for running tasks in the background
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable statusCheck;
    private boolean stateLast = false;
    private long sinceLast = -1L;

    // watch for changes in logfile (don't make local in method)
    private FileObserver watch;
    private String logPath;

    // pick a configuration file to use instead of the built-in one
    private final ActivityResultLauncher<String[]> importConfig =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importConfig);

    // (re-)create activity
    @Override
    protected void onCreate(Bundle BundleSavedInstanceState) {
        super.onCreate(BundleSavedInstanceState);
        // The log view shows the daemon's traffic metadata: keep it out of screenshots and casts.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        // get layout binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        showConfig();
        logPath = DaemonService.log(this).getAbsolutePath();

        // switch off ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // handle margins at top and bottom (SystemBars)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            androidx.core.graphics.Insets systemBars = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
            );
            binding.appBar.setPadding(0, systemBars.top, 0, 0);
            binding.buttons.setPadding(0, 0, 0, systemBars.bottom);
            return windowInsets;
        });

        // handle button clicks (start / stop)
        binding.btnStart.setOnClickListener(v -> startServer());
        binding.btnStop.setOnClickListener(v -> stopServer());
        binding.btnImport.setOnClickListener(v -> importConfig.launch(new String[]{"*/*"}));
        binding.btnBuiltin.setOnClickListener(v -> {
            new File(getFilesDir(), DaemonService.CUSTOM_CONFIG).delete();
            showConfig();
            DaemonService.restart(this);
        });

        // toggle log window visibility
        ImageButton btn = binding.btnLog;
        btn.setOnClickListener(v -> {
            View detail = binding.log;
            if (detail.getVisibility() == View.INVISIBLE) {
                detail.setVisibility(View.VISIBLE);
                btn.setRotation(180f);
            } else {
                detail.setVisibility(View.INVISIBLE);
                btn.setRotation(0f);
            }
        });

        // start watch for changes in logfile
        startWatch();

        // start periodic status check (every second)
        sinceLast = new Date().getTime()/1000;
        statusCheck = new Runnable() {
            @Override
            public void run() {
                // check current daemon state
                boolean currState = checkDaemonStatus();
                // handle state changes
                if (currState != stateLast) {
                    stateLast = currState;
                    sinceLast = new Date().getTime()/1000;
                }
                // display elapsed time.
                String elapsed = "";
                long span = new Date().getTime() / 1000 - sinceLast;
                int hrs = (int) (span / 3600);
                int mins = (int) (span % 3600) / 60;
                if (hrs < 24) {
                    elapsed = String.format(Locale.ENGLISH, getString(R.string.elapsed_short), hrs, mins);
                } else {
                    int days = hrs / 24;
                    hrs = hrs % 24;
                    elapsed = String.format(Locale.ENGLISH, getString(R.string.elapsed_long), hrs, mins);
                }
                binding.elapsed.setText(elapsed);

                // restart in 1 second
                handler.postDelayed(this, 1000);
            }
        };
    }

    // resume operation and handle status checks (again).
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(statusCheck);
        showApps();
    }

    // pause operation and stop status checks.
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusCheck);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        watch.stopWatching();
    }

    // start kpclientd in its foreground service
    private void startServer() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        binding.btnStart.setEnabled(false);
        DaemonService.start(this);
        Toast.makeText(this, R.string.server_started, Toast.LENGTH_SHORT).show();
    }

    // stop server
    private void stopServer() {
        binding.btnStop.setEnabled(false);
        DaemonService.stop(this);
        Toast.makeText(this, R.string.server_stopped, Toast.LENGTH_SHORT).show();
    }

    // show which configuration the daemon uses
    private void showConfig() {
        boolean custom = new File(getFilesDir(), DaemonService.CUSTOM_CONFIG).exists();
        binding.config.setText(getString(R.string.config_src)
                + getString(custom ? R.string.cfg_custom : R.string.cfg_local));
        binding.btnBuiltin.setEnabled(custom);
    }

    // copy the configuration the user picked into the app's own storage
    private void importConfig(Uri uri) {
        if (uri == null) {
            return;
        }
        File out = new File(getFilesDir(), DaemonService.CUSTOM_CONFIG);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("no content");
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        } catch (IOException e) {
            out.delete();
            Toast.makeText(this, R.string.config_import_failed, Toast.LENGTH_SHORT).show();
        }
        showConfig();
        DaemonService.restart(this);
    }

    // list the apps the user answered about, each with a way to take the answer back
    private void showApps() {
        LinearLayout list = binding.apps;
        list.removeAllViews();
        Approvals a = Callers.approvals(this);
        PackageManager pm = getPackageManager();
        for (String pkg : a.allowed()) {
            list.addView(appRow(pm, pkg, getString(R.string.apps_allowed)));
        }
        for (String pkg : a.denied()) {
            list.addView(appRow(pm, pkg, getString(R.string.apps_denied)));
        }
        if (list.getChildCount() == 0) {
            TextView none = new TextView(this);
            none.setText(R.string.apps_none);
            none.setTextColor(0xffdddddd);
            list.addView(none);
        }
    }

    private View appRow(PackageManager pm, String pkg, String state) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setText(Callers.label(pm, pkg) + " (" + state + ")");
        name.setTextColor(0xffffffff);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button forget = new Button(this, null, 0, androidx.appcompat.R.style.Widget_AppCompat_Button_Borderless);
        forget.setText(R.string.apps_forget);
        forget.setTextColor(0xffffffff);
        forget.setOnClickListener(v -> {
            Callers.forget(this, pkg);
            // Ending every connection is the only way to end that app's.
            DaemonService.restart(this);
            showApps();
        });
        row.addView(name);
        row.addView(forget);
        return row;
    }

    // start watching for changes in log file.
    // keep a ring buffer of ten strings from the logfile (a single string
    // can hold multiple log lines) and display them in the log window.
    private void startWatch() {
        File log = new File(logPath);
        String[] body = new String[10];
        Arrays.fill(body, "");
        final long[] pos = {0, 0};

        // start watching
        watch = new FileObserver(new File(log.getParent()), FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && path.equals(log.getName())) {
                    executor.execute(() -> {
                        // check for change in logfile length
                        long curr = log.length();
                        if (curr == pos[1]) {
                            return;
                        }
                        // log truncated: start from beginning
                        if (pos[1] > curr) {
                            pos[1] = 0L;
                        }
                        // read everything since the last read
                        String line = readLog(log, pos[1], curr);
                        if (line == null) line = "";
                        pos[1] = curr;

                        // show the last part of the logfile
                        synchronized (body) {
                            body[(int)pos[0]] = line;
                            pos[0] = (pos[0] + 1) % body.length;
                            StringBuilder show = new StringBuilder();
                            for (int i = 0; i < body.length; i++) {
                                int idx = (int)(pos[0] + i) % body.length;
                                show.append(body[idx]);
                            }
                            handler.post(() -> binding.log.setText(show.toString()));
                            // scroll to bottom
                            binding.log.post(() -> {
                                android.text.Layout layout = binding.log.getLayout();
                                if (layout != null) {
                                    int height = layout.getLineTop(binding.log.getLineCount());
                                    int viewHeight = binding.log.getHeight() - binding.log.getPaddingTop() - binding.log.getPaddingBottom();
                                    int scroll = height - viewHeight;
                                    binding.log.scrollTo(0, Math.max(scroll, 0));
                                }
                            });
                        }
                    });
                }
            }
        };
        watch.startWatching();
    }

    // read logfile from start to end position
    private String readLog(File file, long start, long end) {
        if (!file.exists() || file.length() == 0) {
            return "";
        }
        // limit span to 8k
        if (end - start > 8192L) {
            start = end - 8192L;
        }
        // read section of the log file as a string
        int n = (int) (end - start);
        byte[] buf = new byte[n];
        try (RandomAccessFile log = new RandomAccessFile(file, "r")) {
            // Direkt zur Startposition springen
            log.seek(start);
            log.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getString(R.string.log_read_failed);
    }

    // check the status of the daemon
    private boolean checkDaemonStatus() {
        boolean isRunning = DaemonService.running();
        // switch buttons if required
        if (isRunning) {
            binding.textStatus.setText(R.string.daemon_on);
            binding.btnStart.setEnabled(false);
            binding.btnStop.setEnabled(true);
            return true;
        }
        binding.textStatus.setText(R.string.daemon_off);
        binding.btnStart.setEnabled(true);
        binding.btnStop.setEnabled(false);
        return false;
    }
}
