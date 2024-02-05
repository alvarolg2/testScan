package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
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
import com.itextpdf.layout.property.HorizontalAlignment;
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
                    // La imagen fue capturada y guardada en el Uri proporcionado.
                    Bitmap imageBitmap = getBitmapFromUri(photoURI);
                    convertBitmapToPdf(imageBitmap);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(view -> {
            Log.d("MainActivity", "Botón escanear pulsado.");
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
        Log.d("MainActivity", "Intentando lanzar la cámara.");
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
                imageFileName,  /* prefijo */
                ".jpg",         /* sufijo */
                storageDir      /* directorio */
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

    private void convertBitmapToPdf(Bitmap bitmap) {
        try {
            File pdfDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyApp");
            if (!pdfDir.exists()) pdfDir.mkdir();
            File pdfFile = new File(pdfDir, "image_to_pdf.pdf");

            Log.d("MainActivity", "PDF directory: " + pdfDir.getAbsolutePath());
            Log.d("MainActivity", "PDF file path: " + pdfFile.getAbsolutePath());

            PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            float pdfWidth = 595;
            float pdfHeight = 842;
            float imageWidth = bitmap.getWidth();
            float imageHeight = bitmap.getHeight();
            float scaleFactor = Math.min(pdfWidth / imageWidth, pdfHeight / imageHeight);


            imageWidth = imageWidth * scaleFactor;
            imageHeight = imageHeight * scaleFactor;

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] imageData = byteArrayOutputStream.toByteArray();
            ImageData data = ImageDataFactory.create(imageData);
            Image image = new Image(data).scaleToFit(imageWidth, imageHeight);

            image.setHorizontalAlignment(HorizontalAlignment.CENTER);
            float verticalPosition = (pdfHeight - imageHeight) / 2;
            image.setFixedPosition((pdfWidth - imageWidth) / 2, verticalPosition);

            document.add(image);

            document.close();
            Log.d("MainActivity", "PDF guardado en: " + pdfFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MainActivity", "Error al guardar el PDF.", e);
        }
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
                    // Intent para abrir la pantalla de configuración de la aplicación
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}


