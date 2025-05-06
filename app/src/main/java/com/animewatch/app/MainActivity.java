package com.animewatch.app;

import android.widget.Button;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar loadingProgress;
    private TextView noInternetText;
    private LinearLayout buttonContainer;
    private LinearLayout footerContainer;
    private Button selectedButton = null;
    private static final String HOME_URL = "https://hianimez.to/home";
    private static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/easy-watch/AppUpdates/refs/heads/main/update.json";
    private static final String PREFS_NAME = "AnimeWatchPrefs";
    private static final String PREF_LAST_DOWNLOADED_VERSION = "last_downloaded_version";
    private static final String PREF_LAST_CHECK_TIME = "last_check_time";
    private static final long UPDATE_CHECK_INTERVAL = 24 * 60 * 60 * 1000;
    private static final String KEY_WEBVIEW_URL = "webview_url";
    private static final String KEY_VIDEO_POSITION = "video_position";
    private static final String KEY_SCROLL_POSITION = "scroll_position";

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLoading = false;
    private Runnable loadingRunnable;
    private boolean buttonsLoaded = false;
    private boolean iconsLoaded = false;

    private Set<String> allowedLinks = new HashSet<String>();
    private Set<String> adDomains = new HashSet<String>();

    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalOrientation;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Object prefsLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adDomains.add("doubleclick.net");
        adDomains.add("googlesyndication.com");
        adDomains.add("admob.com");
        adDomains.add("adservice.google.com");
        adDomains.add("adnxs.com");

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);

        webView = (WebView) findViewById(R.id.webView);
        loadingProgress = (ProgressBar) findViewById(R.id.loading_progress);
        noInternetText = (TextView) findViewById(R.id.no_internet_text);
        buttonContainer = (LinearLayout) findViewById(R.id.button_container);
        footerContainer = (LinearLayout) findViewById(R.id.footer);

        customViewContainer = new FrameLayout(this);
        customViewContainer.setLayoutParams(new FrameLayout.LayoutParams(
												ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        customViewContainer.setBackgroundColor(Color.BLACK);

        configureWebView(webView);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        webView.setWebViewClient(new MyWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
				@Override
				public void onShowCustomView(View view, CustomViewCallback callback) {
					if (customView != null) {
						onHideCustomView();
						return;
					}

					customView = view;
					customViewCallback = callback;
					originalOrientation = getRequestedOrientation();

					webView.setVisibility(View.GONE);
					loadingProgress.setVisibility(View.GONE);
					noInternetText.setVisibility(View.GONE);
					buttonContainer.setVisibility(View.GONE);
					footerContainer.setVisibility(View.GONE);

					customViewContainer.addView(customView);
					((ViewGroup) findViewById(android.R.id.content)).addView(customViewContainer);

					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
					getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				}

				@Override
				public void onHideCustomView() {
					if (customView == null) return;

					webView.setVisibility(View.VISIBLE);
					updateViewVisibility();
					buttonContainer.setVisibility(View.VISIBLE);
					footerContainer.setVisibility(View.VISIBLE);

					customViewContainer.removeView(customView);
					((ViewGroup) findViewById(android.R.id.content)).removeView(customViewContainer);

					customView = null;
					if (customViewCallback != null) {
						customViewCallback.onCustomViewHidden();
						customViewCallback = null;
					}

					setRequestedOrientation(originalOrientation);
					getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				}
			});

        loadAllowedLinks();
        updateViewVisibility();
        loadButtonsFromJson();
        setupSocialMediaIcons();
        checkForUpdate(null);

        if (savedInstanceState != null) {
            String savedUrl = savedInstanceState.getString(KEY_WEBVIEW_URL);
            if (savedUrl != null && !savedUrl.isEmpty()) {
                webView.loadUrl(savedUrl);
            }
            long videoPosition = savedInstanceState.getLong(KEY_VIDEO_POSITION, -1);
            if (videoPosition >= 0) {
                final long finalVideoPosition = videoPosition;
                handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							webView.loadUrl("javascript:(function() { " +
											"function setVideoPosition() { " +
											"    var videos = document.getElementsByTagName('video'); " +
											"    if (videos.length > 0) { " +
											"        for (var i = 0; i < videos.length; i++) { " +
											"            videos[i].currentTime = " + finalVideoPosition / 1000 + "; " +
											"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"        } " +
											"    } else { " +
											"        var iframes = document.getElementsByTagName('iframe'); " +
											"        for (var j = 0; j < iframes.length; j++) { " +
											"            if (iframes[j].contentDocument) { " +
											"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
											"                for (var k = 0; k < iframeVideos.length; k++) { " +
											"                    iframeVideos[k].currentTime = " + finalVideoPosition / 1000 + "; " +
											"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"                } " +
											"            } " +
											"        } " +
											"    } " +
											"    document.addEventListener('loadedmetadata', function() { " +
											"        var videos = document.getElementsByTagName('video'); " +
											"        for (var i = 0; i < videos.length; i++) { " +
											"            videos[i].currentTime = " + finalVideoPosition / 1000 + "; " +
											"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"        } " +
											"        var iframes = document.getElementsByTagName('iframe'); " +
											"        for (var j = 0; j < iframes.length; j++) { " +
											"            if (iframes[j].contentDocument) { " +
											"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
											"                for (var k = 0; k < iframeVideos.length; k++) { " +
											"                    iframeVideos[k].currentTime = " + finalVideoPosition / 1000 + "; " +
											"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"                } " +
											"            } " +
											"        } " +
											"    }, { once: true }); " +
											"})()");
						}
					}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
            }
            int scrollPosition = savedInstanceState.getInt(KEY_SCROLL_POSITION, 0);
            if (scrollPosition > 0) {
                final int finalScrollPosition = scrollPosition;
                handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							webView.scrollTo(0, finalScrollPosition);
						}
					}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
            }
        }
    }

    private void configureWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);

        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath(getCacheDir().getPath());

        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadsImagesAutomatically(true);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidInterface");
    }

    private void loadAllowedLinks() {
        try {
            InputStream is = getAssets().open("links.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(jsonString.toString());
            JSONArray buttons = json.getJSONArray("buttons");

            for (int i = 0; i < buttons.length(); i++) {
                JSONObject linkData = buttons.getJSONObject(i);
                String url = linkData.getString("url");
                if (url != null && !url.isEmpty()) {
                    allowedLinks.add(url);
                }
            }
            allowedLinks.add("file:///android_asset/about.html");
            allowedLinks.add(UPDATE_JSON_URL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateViewVisibility() {
        if (isNetworkConnected()) {
            noInternetText.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            if (isLoading) {
                loadingProgress.setVisibility(View.VISIBLE);
            } else {
                loadingProgress.setVisibility(View.GONE);
                if (webView.getUrl() == null) {
                    handler.post(new Runnable() {
							@Override
							public void run() {
								webView.loadUrl(HOME_URL);
							}
						});
                }
            }
        } else {
            noInternetText.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
            loadingProgress.setVisibility(View.GONE);
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void loadButtonsFromJson() {
        if (buttonsLoaded) return;
        try {
            InputStream is = getAssets().open("links.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(jsonString.toString());
            JSONArray buttons = json.getJSONArray("buttons");

            buttonContainer.removeAllViews();
            for (int i = 0; i < buttons.length(); i++) {
                JSONObject linkData = buttons.getJSONObject(i);
                final String buttonUrl = linkData.getString("url");
                final String buttonText = linkData.getString("text");

                final Button button = new Button(this);
                button.setText(buttonText);
                button.setBackgroundResource(R.drawable.button_background);
                button.setTextColor(Color.WHITE);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(8, 8, 8, 8);
                button.setLayoutParams(params);

                button.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if (selectedButton != null) {
								selectedButton.setBackgroundResource(R.drawable.button_background);
								selectedButton.setTextColor(Color.WHITE);
							}
							selectedButton = button;
							selectedButton.setBackgroundResource(R.drawable.selected_button_background);
							selectedButton.setTextColor(Color.WHITE);
							webView.loadUrl(buttonUrl);
						}
					});

                buttonContainer.addView(button);
            }
            buttonsLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupSocialMediaIcons() {
        if (iconsLoaded) return;
        footerContainer.removeAllViews(); // পুরোনো আইকনগুলো সরিয়ে ফেলা

        String[][] links = {
            {"home", "", "ic_home"},
            {"about", "file:///android_asset/about.html", "ic_about"},
            {"update", "", "update"},
            {"login", "", "ic_login"}, // লগইন আইকন যোগ
            {"chat", "", "ic_chat"},   // চ্যাট আইকন যোগ
            {"refresh", "", "ic_refresh"} // শেষে রিফ্রেশ আইকন
        };

        for (int i = 0; i < links.length; i++) {
            final String name = links[i][0];
            final String url = links[i][1];
            final String iconName = links[i][2];

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            containerParams.setMargins(10, 0, 10, 0);
            container.setLayoutParams(containerParams);
            container.setGravity(android.view.Gravity.CENTER);

            final ImageView iconView = new ImageView(this);
            int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
            if (resId != 0) {
                iconView.setImageResource(resId);
            } else {
                iconView.setImageResource(android.R.drawable.ic_dialog_alert);
                Toast.makeText(this, "Icon '" + iconName + "' not found in res/drawable", Toast.LENGTH_LONG).show();
            }
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(60, 60);
            iconParams.setMargins(0, 0, 0, 5);
            iconView.setLayoutParams(iconParams);

            iconView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						ScaleAnimation anim = new ScaleAnimation(1f, 1.2f, 1f, 1.2f,
																 Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
						anim.setDuration(150);
						anim.setRepeatCount(1);
						anim.setRepeatMode(Animation.REVERSE);
						iconView.startAnimation(anim);

						if ("home".equals(name)) {
							isLoading = true;
							updateViewVisibility();
							webView.loadUrl(HOME_URL);
						} else if ("about".equals(name)) {
							isLoading = true;
							updateViewVisibility();
							webView.loadUrl("file:///android_asset/about.html");
						} else if ("update".equals(name)) {
							checkForUpdate(null);
						} else if ("login".equals(name)) {
							startActivity(new Intent(MainActivity.this, LoginActivity.class));
						} else if ("chat".equals(name)) {
							startActivity(new Intent(MainActivity.this, ChatActivity.class));
						} else if ("refresh".equals(name)) {
							isLoading = true;
							updateViewVisibility();
							if (webView.getUrl() != null) {
								webView.reload();
							} else {
								Toast.makeText(MainActivity.this, "No page to refresh", Toast.LENGTH_SHORT).show();
							}
						}
					}
				});

            TextView label = new TextView(this);
            label.setText(name.substring(0, 1).toUpperCase() + name.substring(1));
            label.setTextColor(Color.WHITE);
            label.setTextSize(10);
            label.setGravity(android.view.Gravity.CENTER);

            container.addView(iconView);
            container.addView(label);
            footerContainer.addView(container);
        }
        iconsLoaded = true;
    }

    public void checkForUpdate(View view) {
        if (!isNetworkConnected()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        checkForUpdate();
    }

    private void checkForUpdate() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 0);
        long currentTime = System.currentTimeMillis();
        final long finalCurrentTime = currentTime;

        if (currentTime - lastCheckTime < UPDATE_CHECK_INTERVAL) {
            handler.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(MainActivity.this, "You have the latest version", Toast.LENGTH_SHORT).show();
					}
				});
            return;
        }

        if (!isNetworkConnected()) {
            handler.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(MainActivity.this, "No internet connection", Toast.LENGTH_SHORT).show();
					}
				});
            return;
        }

        executorService.execute(new Runnable() {
				@Override
				public void run() {
					HttpURLConnection connection = null;
					BufferedReader reader = null;
					try {
						URL url = new URL(UPDATE_JSON_URL);
						connection = (HttpURLConnection) url.openConnection();
						connection.setRequestMethod("GET");
						connection.setConnectTimeout(5000);
						connection.setReadTimeout(5000);
						connection.setRequestProperty("User-Agent", "Mozilla/5.0");
						connection.setRequestProperty("Accept", "*/*");
						connection.setUseCaches(false);
						connection.connect();

						int responseCode = connection.getResponseCode();
						if (responseCode != HttpURLConnection.HTTP_OK) {
							throw new Exception("HTTP error code: " + responseCode);
						}

						InputStream inputStream = connection.getInputStream();
						reader = new BufferedReader(new InputStreamReader(inputStream));
						StringBuilder json = new StringBuilder();
						String line;
						while ((line = reader.readLine()) != null) {
							json.append(line);
						}

						JSONObject obj = new JSONObject(json.toString());
						JSONObject updateInfo = obj.getJSONObject("update_info");
						final String newVersionName = updateInfo.getString("version_name");
						final String downloadUrl = updateInfo.getString("apk_url");
						final String releaseNotes = updateInfo.optString("release_notes", "No release notes available");

						String currentVersionName = "0.0.0";
						try {
							PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
							currentVersionName = packageInfo.versionName != null ? packageInfo.versionName : "0.0.0";
						} catch (NameNotFoundException e) {
							e.printStackTrace();
						}

						String lastDownloadedVersion;
						synchronized (prefsLock) {
							lastDownloadedVersion = prefs.getString(PREF_LAST_DOWNLOADED_VERSION, "0.0.0");
						}

						if (currentVersionName.equals(lastDownloadedVersion)) {
							handler.post(new Runnable() {
									@Override
									public void run() {
										Toast.makeText(MainActivity.this, "You have the latest version", Toast.LENGTH_SHORT).show();
									}
								});
						} else if (isNewerVersion(newVersionName, currentVersionName)) {
							handler.post(new Runnable() {
									@Override
									public void run() {
										showUpdateDialog(downloadUrl, newVersionName, releaseNotes);
									}
								});
						} else {
							handler.post(new Runnable() {
									@Override
									public void run() {
										Toast.makeText(MainActivity.this, "You have the latest version", Toast.LENGTH_SHORT).show();
									}
								});
						}

						synchronized (prefsLock) {
							SharedPreferences.Editor editor = prefs.edit();
							editor.putLong(PREF_LAST_CHECK_TIME, finalCurrentTime);
							editor.apply();
						}

					} catch (Exception e) {
						e.printStackTrace();
						final String errorMessage = e.getMessage();
						handler.post(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(MainActivity.this, "Error checking for updates: " + errorMessage, Toast.LENGTH_LONG).show();
								}
							});
					} finally {
						if (reader != null) {
							try {
								reader.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if (connection != null) {
							connection.disconnect();
						}
					}
				}
			});
    }

    private boolean isNewerVersion(String newVersion, String currentVersion) {
        if (newVersion == null || currentVersion == null) return false;

        String[] newParts = newVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");
        int length = Math.max(newParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int newPart = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (newPart > currentPart) return true;
            if (newPart < currentPart) return false;
        }
        return false;
    }

    private void showUpdateDialog(final String downloadUrl, final String newVersionName, final String releaseNotes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Available");
        builder.setMessage("A new version (" + newVersionName + ") is available.\n\nChanges:\n" + releaseNotes);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					synchronized (prefsLock) {
						getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
							.edit()
							.putString(PREF_LAST_DOWNLOADED_VERSION, newVersionName)
							.apply();
					}
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
					startActivity(intent);
					finish();
				}
			});
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// No action
				}
			});
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (customView != null) {
            customViewContainer.setLayoutParams(new FrameLayout.LayoutParams(
													ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView.getUrl() != null) {
            outState.putString(KEY_WEBVIEW_URL, webView.getUrl());
        }
        // ভিডিও পজিশন সংরক্ষণ
        webView.loadUrl("javascript:(function() { " +
						"function findVideos(element) { " +
						"    var videos = element.getElementsByTagName('video'); " +
						"    for (var i = 0; i < videos.length; i++) { " +
						"        if (!isNaN(videos[i].currentTime)) { " +
						"            AndroidInterface.saveVideoPosition(Math.round(videos[i].currentTime * 1000)); " +
						"        } " +
						"    } " +
						"    var iframes = element.getElementsByTagName('iframe'); " +
						"    for (var j = 0; j < iframes.length; j++) { " +
						"        if (iframes[j].contentDocument) { " +
						"            findVideos(iframes[j].contentDocument); " +
						"        } " +
						"    } " +
						"} " +
						"findVideos(document); " +
						"})()");
        synchronized (prefsLock) {
            outState.putLong(KEY_VIDEO_POSITION, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_VIDEO_POSITION, -1));
            outState.putInt(KEY_SCROLL_POSITION, webView.getScrollY());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            String savedUrl = savedInstanceState.getString(KEY_WEBVIEW_URL);
            if (savedUrl != null && !savedUrl.isEmpty()) {
                webView.loadUrl(savedUrl);
            }
            final long videoPosition = savedInstanceState.getLong(KEY_VIDEO_POSITION, -1);
            if (videoPosition >= 0) {
                handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							webView.loadUrl("javascript:(function() { " +
											"function setVideoPosition() { " +
											"    var videos = document.getElementsByTagName('video'); " +
											"    if (videos.length > 0) { " +
											"        for (var i = 0; i < videos.length; i++) { " +
											"            videos[i].currentTime = " + videoPosition / 1000 + "; " +
											"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"        } " +
											"    } else { " +
											"        var iframes = document.getElementsByTagName('iframe'); " +
											"        for (var j = 0; j < iframes.length; j++) { " +
											"            if (iframes[j].contentDocument) { " +
											"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
											"                for (var k = 0; k < iframeVideos.length; k++) { " +
											"                    iframeVideos[k].currentTime = " + videoPosition / 1000 + "; " +
											"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"                } " +
											"            } " +
											"        } " +
											"    } " +
											"    document.addEventListener('loadedmetadata', function() { " +
											"        var videos = document.getElementsByTagName('video'); " +
											"        for (var i = 0; i < videos.length; i++) { " +
											"            videos[i].currentTime = " + videoPosition / 1000 + "; " +
											"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"        } " +
											"        var iframes = document.getElementsByTagName('iframe'); " +
											"        for (var j = 0; j < iframes.length; j++) { " +
											"            if (iframes[j].contentDocument) { " +
											"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
											"                for (var k = 0; k < iframeVideos.length; k++) { " +
											"                    iframeVideos[k].currentTime = " + videoPosition / 1000 + "; " +
											"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"                } " +
											"            } " +
											"        } " +
											"    }, { once: true }); " +
											"})()");
						}
					}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
            }
            final int scrollPosition = savedInstanceState.getInt(KEY_SCROLL_POSITION, 0);
            if (scrollPosition > 0) {
                handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							webView.scrollTo(0, scrollPosition);
						}
					}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        if (loadingRunnable != null) {
            handler.removeCallbacks(loadingRunnable);
        }
        // ভিডিও পজিশন সংরক্ষণ
        webView.loadUrl("javascript:(function() { " +
						"function findVideos(element) { " +
						"    var videos = element.getElementsByTagName('video'); " +
						"    for (var i = 0; i < videos.length; i++) { " +
						"        if (!isNaN(videos[i].currentTime)) { " +
						"            AndroidInterface.saveVideoPosition(Math.round(videos[i].currentTime * 1000)); " +
						"        } " +
						"    } " +
						"    var iframes = element.getElementsByTagName('iframe'); " +
						"    for (var j = 0; j < iframes.length; j++) { " +
						"        if (iframes[j].contentDocument) { " +
						"            findVideos(iframes[j].contentDocument); " +
						"        } " +
						"    } " +
						"} " +
						"findVideos(document); " +
						"})()");
        // স্ক্রল পজিশন সংরক্ষণ
        synchronized (prefsLock) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putInt(KEY_SCROLL_POSITION, webView.getScrollY());
            editor.apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        updateViewVisibility();
        long videoPosition;
        int scrollPosition;
        synchronized (prefsLock) {
            videoPosition = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_VIDEO_POSITION, -1);
            scrollPosition = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_SCROLL_POSITION, 0);
        }
        final long finalVideoPosition = videoPosition;
        if (finalVideoPosition >= 0) {
            handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						webView.loadUrl("javascript:(function() { " +
										"function setVideoPosition() { " +
										"    var videos = document.getElementsByTagName('video'); " +
										"    if (videos.length > 0) { " +
										"        for (var i = 0; i < videos.length; i++) { " +
										"            videos[i].currentTime = " + finalVideoPosition / 1000 + "; " +
										"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
										"        } " +
										"    } else { " +
										"        var iframes = document.getElementsByTagName('iframe'); " +
										"        for (var j = 0; j < iframes.length; j++) { " +
										"            if (iframes[j].contentDocument) { " +
										"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
										"                for (var k = 0; k < iframeVideos.length; k++) { " +
										"                    iframeVideos[k].currentTime = " + finalVideoPosition / 1000 + "; " +
										"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
										"                } " +
										"            } " +
										"        } " +
										"    } " +
										"    document.addEventListener('loadedmetadata', function() { " +
										"        var videos = document.getElementsByTagName('video'); " +
										"        for (var i = 0; i < videos.length; i++) { " +
										"            videos[i].currentTime = " + finalVideoPosition / 1000 + "; " +
										"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
										"        } " +
										"        var iframes = document.getElementsByTagName('iframe'); " +
										"        for (var j = 0; j < iframes.length; j++) { " +
										"            if (iframes[j].contentDocument) { " +
										"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
										"                for (var k = 0; k < iframeVideos.length; k++) { " +
										"                    iframeVideos[k].currentTime = " + finalVideoPosition / 1000 + "; " +
										"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
										"                } " +
										"            } " +
										"        } " +
										"    }, { once: true }); " +
										"})()");
					}
				}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
        }
        final int finalScrollPosition = scrollPosition;
        if (finalScrollPosition > 0) {
            handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						webView.scrollTo(0, finalScrollPosition);
					}
				}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.stopLoading();
            webView.clearCache(false);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        handler.removeCallbacksAndMessages(null);
        executorService.shutdown();
    }

    private long getSavedVideoPosition() {
        synchronized (prefsLock) {
            return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_VIDEO_POSITION, -1);
        }
    }

    private class WebAppInterface {
        private MainActivity mainActivity;

        public WebAppInterface(MainActivity activity) {
            this.mainActivity = activity;
        }

        @JavascriptInterface
        public void sendEmail(String email, String message) {
            Toast.makeText(mainActivity, "Email: " + email + "\nMessage: " + message, Toast.LENGTH_LONG).show();
            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("message/rfc822");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"mgolok253@gmail.com"});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Contact Form Submission");
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Hi (from: " + email + ")\n\n" + message);
            mainActivity.startActivity(Intent.createChooser(emailIntent, "Send Email"));
        }

        @JavascriptInterface
        public void saveVideoPosition(long position) {
            if (position < 0) {
                return; // যদি মান ঠিক না হয়, সংরক্ষণ করা হবে না
            }
            synchronized (prefsLock) {
                SharedPreferences.Editor editor = mainActivity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putLong(KEY_VIDEO_POSITION, position);
                editor.apply();
            }
        }
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                Iterator<String> iterator = adDomains.iterator();
                while (iterator.hasNext()) {
                    String adDomain = iterator.next();
                    if (host.contains(adDomain)) {
                        return new WebResourceResponse("text/plain", "utf-8", null);
                    }
                }
            }
            return super.shouldInterceptRequest(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            isLoading = true;
            updateViewVisibility();

            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            String currentUrl = view.getUrl() != null ? view.getUrl() : "";

            if (url.equals("file:///android_asset/about.html") || url.equals(UPDATE_JSON_URL)) {
                view.loadUrl(url);
                return true;
            }

            if (host != null && currentUrl.contains(host)) {
                if (allowedLinks.contains(url)) {
                    Toast.makeText(MainActivity.this, "This link can only be accessed from the scroll bar", Toast.LENGTH_SHORT).show();
                    view.loadUrl(currentUrl);
                    return true;
                }
                view.loadUrl(url);
                return true;
            }

            if (allowedLinks.contains(url)) {
                view.loadUrl(url);
                return true;
            }

            Toast.makeText(MainActivity.this, "This link is not allowed", Toast.LENGTH_SHORT).show();
            view.loadUrl(HOME_URL);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            isLoading = true;
            updateViewVisibility();
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (loadingRunnable != null) {
                handler.removeCallbacks(loadingRunnable);
            }

            long videoPosition;
            int scrollPosition;
            synchronized (prefsLock) {
                videoPosition = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_VIDEO_POSITION, -1);
                scrollPosition = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_SCROLL_POSITION, 0);
            }
            final long finalVideoPosition = videoPosition;
            if (finalVideoPosition >= 0 && url.equals(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_WEBVIEW_URL, ""))) {
                handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							webView.loadUrl("javascript:(function() { " +
											"function setVideoPosition() { " +
											"    var videos = document.getElementsByTagName('video'); " +
											"    if (videos.length > 0) { " +
											"        for (var i = 0; i < videos.length; i++) { " +
											"            videos[i].currentTime = " + finalVideoPosition / 1000 + "; " +
											"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"        } " +
											"    } else { " +
											"        var iframes = document.getElementsByTagName('iframe'); " +
											"        for (var j = 0; j < iframes.length; j++) { " +
											"            if (iframes[j].contentDocument) { " +
											"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
											"                for (var k = 0; k < iframeVideos.length; k++) { " +
											"                    iframeVideos[k].currentTime = " + finalVideoPosition / 1000 + "; " +
											"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"                } " +
											"            } " +
											"        } " +
											"    } " +
											"    document.addEventListener('loadedmetadata', function() { " +
											"        var videos = document.getElementsByTagName('video'); " +
											"        for (var i = 0; i < videos.length; i++) { " +
											"            videos[i].currentTime = " + finalVideoPosition / 1000 + "; " +
											"            videos[i].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"        } " +
											"        var iframes = document.getElementsByTagName('iframe'); " +
											"        for (var j = 0; j < iframes.length; j++) { " +
											"            if (iframes[j].contentDocument) { " +
											"                var iframeVideos = iframes[j].contentDocument.getElementsByTagName('video'); " +
											"                for (var k = 0; k < iframeVideos.length; k++) { " +
											"                    iframeVideos[k].currentTime = " + finalVideoPosition / 1000 + "; " +
											"                    iframeVideos[k].play().catch(function(e) { console.log('Play error: ' + e); }); " +
											"                } " +
											"            } " +
											"        } " +
											"    }, { once: true }); " +
											"})()");
						}
					}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
            }

            final int finalScrollPosition = scrollPosition;
            if (finalScrollPosition > 0) {
                handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							webView.scrollTo(0, finalScrollPosition);
						}
					}, 1500); // ১৫০০ মিলিসেকেন্ড ডিলে
            }

            loadingRunnable = new Runnable() {
                @Override
                public void run() {
                    isLoading = false;
                    updateViewVisibility();
                    loadingRunnable = null;
                }
            };
            handler.postDelayed(loadingRunnable, 0);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            isLoading = false;
            updateViewVisibility();
            if (errorCode == WebViewClient.ERROR_HOST_LOOKUP) {
                Toast.makeText(MainActivity.this, "This page is not available", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Error: " + description, Toast.LENGTH_SHORT).show();
            }
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }
}
