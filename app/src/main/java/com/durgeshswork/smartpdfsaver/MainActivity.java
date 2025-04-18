package com.durgeshswork.smartpdfsaver;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.rendering.PDFRenderer;


public class MainActivity extends AppCompatActivity {

        private static final int PICK_PDF_FILES = 1001;
        private List<Uri> selectedUris = new ArrayList<>();
        private TextView txtSelectedFiles;
        private Button btnSplit;
        LottieAnimationView pressed;
        Button openColorPdfBtn,openBwPdfBtn,clearbutt;
        private InterstitialAd interstitialAd;
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            PDFBoxResourceLoader.init(getApplicationContext()); // REQUIRED!
            setContentView(R.layout.activity_main);
            EdgeToEdge.enable(this);

            MobileAds.initialize(this, initializationStatus -> {});

            loadInterstitialAd();
            Button btnSelectPdf = findViewById(R.id.btnSelectPdf);
            txtSelectedFiles = findViewById(R.id.txtSelectedFiles);
            btnSplit = findViewById(R.id.btnSplit);
            pressed=findViewById(R.id.pressed);
            btnSplit.setEnabled(false);
             openColorPdfBtn = findViewById(R.id.openColorPdfBtn);
             openBwPdfBtn = findViewById(R.id.openBwPdfBtn);
             clearbutt=findViewById(R.id.clrbutton);

            btnSelectPdf.setOnClickListener(v -> pickMultiplePdfFiles());

            clearbutt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectedUris.clear();
                    btnSplit.setVisibility(VISIBLE);
                    txtSelectedFiles.setText("No files selected");
                    btnSplit.setEnabled(false);
                    openColorPdfBtn.setVisibility(GONE);
                    openBwPdfBtn.setVisibility(GONE);
                    clearbutt.setVisibility(GONE);
                }
            });
            btnSplit.setOnClickListener(v -> {
                btnSplit.setVisibility(GONE);
                Toast.makeText(this, "Processing " + selectedUris.size() + " file(s)...", Toast.LENGTH_SHORT).show();
                // TODO: Add PDF color vs. B&W splitting logic here
                splitPdfByColor(MainActivity.this, selectedUris, pressed,openColorPdfBtn,openBwPdfBtn,clearbutt);  // 'this' refers to your Activity or Context
            });
        }
    private void showAdOrProceed() {

        if (interstitialAd != null) {

            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {

                    Log.d("AdMob", "Ad Dismissed - Moving to Next Screen");
                    loadInterstitialAd();
                    openColorPdfBtn.setVisibility(VISIBLE);
                    openBwPdfBtn.setVisibility(VISIBLE);

                }
                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    Log.e("AdMob", "Ad Failed to Show: " + adError.getMessage() );
                    openColorPdfBtn.setVisibility(VISIBLE);
                    openBwPdfBtn.setVisibility(VISIBLE);
                }
            });
            interstitialAd.show(this);
        }
        else{
            Log.d("AdMob", "Ad Not Ready - Moving to Next Screen");
            openColorPdfBtn.setVisibility(VISIBLE);
            openBwPdfBtn.setVisibility(VISIBLE);

        }

    }

    public void splitPdfByColor(Activity activity, List<Uri> pdfUris, View pressed, View openColorPdfBtn, View openBwPdfBtn,View clrbutton) {
        new Thread(() -> {
            try {
                activity.runOnUiThread(() -> pressed.setVisibility(VISIBLE));
                activity.runOnUiThread(()->{
                    pressed.setVisibility(VISIBLE);
                    clrbutton.setVisibility(GONE);
                });
                PDDocument colorDoc = new PDDocument();
                PDDocument bwDoc = new PDDocument();

                for (Uri pdfUri : pdfUris) {
                    try {
                        Log.d("PDFSplit", "Processing: " + pdfUri.toString());

                        InputStream inputStream = activity.getContentResolver().openInputStream(pdfUri);
                        PDDocument document = PDDocument.load(inputStream);
                        PDFRenderer renderer = new PDFRenderer(document);

                        for (int i = 0; i < document.getNumberOfPages(); i++) {
                            try {
                                Bitmap bmp = renderer.renderImageWithDPI(i, 100); // 100 DPI
                                PDPage newPage = new PDPage();
                                PDDocument targetDoc = isColor(bmp) ? colorDoc : bwDoc;

                                targetDoc.addPage(newPage);

                                PDImageXObject image = LosslessFactory.createFromImage(targetDoc, bmp);

                                PDPageContentStream contentStream = new PDPageContentStream(targetDoc, newPage);
                                contentStream.drawImage(image, 0, 0, newPage.getMediaBox().getWidth(), newPage.getMediaBox().getHeight());
                                contentStream.close();
                                bmp.recycle(); // after you're done with the bitmap

                            } catch (Exception e) {
                                Log.d("PDFSplit", "Page render failed: " + e.getMessage());
                            }
                        }

                        document.close();
                    } catch (Exception e) {
                        Log.d("PDFSplit", "File error: " + e.getMessage());
                    }
                }

                File dir = new File(activity.getExternalFilesDir(null), "SplitPDFs");
                if (!dir.exists()) dir.mkdirs();

                File colorFile = new File(dir, "Merged_Color.pdf");
                File bwFile = new File(dir, "Merged_BW.pdf");

                colorDoc.save(colorFile);
                bwDoc.save(bwFile);



                Log.d("PDFSplit", "Merge & split complete");

                activity.runOnUiThread(() -> {
                    pressed.setVisibility(View.GONE);
                    clrbutton.setVisibility(VISIBLE);
                    Toast.makeText(activity, "Split & merge complete!", Toast.LENGTH_SHORT).show();
                    showAdOrProceed();
                    openColorPdfBtn.setOnClickListener(v -> openPdf(activity, colorFile));
                    openBwPdfBtn.setOnClickListener(v -> openPdf(activity, bwFile));


                });
                colorDoc.close();
                bwDoc.close();

            } catch (Exception e) {
                Log.e("PDFSplit", "Processing failed: " + e.getMessage());
                activity.runOnUiThread(() -> {
                    pressed.setVisibility(View.GONE);
                    Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    private void openPdf(Context context, File file) {
        Uri pdfUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".provider",
                file
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(pdfUri, "application/pdf");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No app found to open PDF", Toast.LENGTH_SHORT).show();
        }
    }

    boolean isColor(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int step = 10; // for faster sampling

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int pixel = bmp.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                if (!(Math.abs(r - g) < 10 && Math.abs(r - b) < 10 && Math.abs(g - b) < 10)) {
                    return true; // colored pixel found
                }
            }
        }
        return false; // all sampled pixels are grayscale
    }
    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this,"ca-app-pub-4669128722580003/8640463809" , adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        Log.d("AdMob", "Ad Loaded Successfully");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        interstitialAd = null;
                        Log.e("AdMob", "Ad Failed to Load: " + adError.getMessage());
                    }
                });
    }
    private void pickMultiplePdfFiles() {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select PDF files"), PICK_PDF_FILES);

        }
        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == PICK_PDF_FILES && resultCode == RESULT_OK) {
                selectedUris.clear();

                if (data != null) {
                    if (data.getClipData() != null) {
                        ClipData clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            Uri uri = clipData.getItemAt(i).getUri();
                            selectedUris.add(uri);
                        }
                    } else if (data.getData() != null) {
                        selectedUris.add(data.getData());
                    }

                    StringBuilder names = new StringBuilder("Selected files:\n");
                    for (Uri uri : selectedUris) {
                        names.append(getFileName(uri)).append("\n");
                    }

                    txtSelectedFiles.setText(names.toString());
                    btnSplit.setEnabled(!selectedUris.isEmpty());
                }
            }
        }
        private String getFileName(Uri uri) {
            String result = "Unknown.pdf";
            if (uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (index >= 0) {
                            result = cursor.getString(index);
                        }
                    }
                }
            } else {
                result = uri.getLastPathSegment();
            }
            return result;
        }

    }

