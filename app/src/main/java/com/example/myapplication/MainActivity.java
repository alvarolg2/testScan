package com.example.myapplication;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private Uri photoURI;

    private ActivityResultLauncher<Intent> mTakePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Bitmap imageBitmap = getBitmapFromUri(photoURI);
                    promptForFileName(imageBitmap);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                requestCameraPermission();
            }
        });
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_IMAGE_CAPTURE);
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (Exception ex) {
                Log.e("MainActivity", "Error al crear el archivo de imagen.", ex);
            }
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                mTakePictureLauncher.launch(takePictureIntent);
            }
        } else {
            Log.d("MainActivity", "No se encontró ninguna actividad de cámara compatible.");
        }
    }

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("MainActivity", "Error al obtener el Bitmap desde el Uri.", e);
        }
        return bitmap;
    }

    private void convertBitmapToPdf(Bitmap bitmap, String pdfFileName) {
        ProgressBar progressBar = findViewById(R.id.progressBar);
        // Mostrar el ProgressBar en el hilo de UI
        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));

        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + File.separator + "MyApp");

                    Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

                    if (uri != null) {
                        try (PdfWriter writer = new PdfWriter(getContentResolver().openOutputStream(uri))) {
                            createPdf(bitmap, writer);
                        }
                    }
                } else {
                    File documentsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MyApp");
                    if (!documentsDir.exists()) {
                        documentsDir.mkdirs();
                    }
                    File pdfFile = new File(documentsDir, pdfFileName);

                    try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile))) {
                        createPdf(bitmap, writer);
                    }
                }
                // Mostrar mensaje de éxito en el hilo de UI
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "PDF generado correctamente.", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("MainActivity", "Error al guardar el PDF.", e);
                // Mostrar mensaje de error en el hilo de UI
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error al generar el PDF.", Toast.LENGTH_SHORT).show());
            } finally {
                // Ocultar el ProgressBar en el hilo de UI
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        }).start();
    }




    private void createPdf(Bitmap bitmap, PdfWriter writer) {
        PdfDocument pdfDocument = new PdfDocument(writer);
        Document document = new Document(pdfDocument);
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] imageData = byteArrayOutputStream.toByteArray();
            ImageData data = ImageDataFactory.create(imageData);
            Image image = new Image(data);
            document.add(image);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MainActivity", "Error al añadir la imagen al PDF.", e);
        } finally {
            document.close();
        }
        Log.d("MainActivity", "PDF guardado.");
    }

    private void promptForFileName(Bitmap bitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nombre del archivo PDF");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String pdfFileName = input.getText().toString();
            if (!pdfFileName.endsWith(".pdf")) {
                pdfFileName += ".pdf";
            }
            convertBitmapToPdf(bitmap, pdfFileName);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Log.d("MainActivity", "Permiso de cámara denegado.");
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso denegado")
                .setMessage("Este permiso es necesario para capturar fotos. Por favor, habilita el permiso desde la configuración de la aplicación.")
                .setPositiveButton("Configuración", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}


