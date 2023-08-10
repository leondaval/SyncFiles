package leondaval.SyncFiles;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.Manifest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button copyDirectoryButton = findViewById(R.id.copyDirectoryButton);
        copyDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission())
                    copy();
                 else
                    requestPermission();
            }
        });

        Button moveDirectoryButton = findViewById(R.id.moveDirectoryButton);
        moveDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission())
                    move();
                else
                    requestPermission();
            }
        });

        Button syncDirectoryButton = findViewById(R.id.syncDirectoryButton);
        syncDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission() && checkPermissionInternet()) {
                    showSmbCredentialsDialog();
                }   else{
                    if(!checkPermission())
                        requestPermission();
                    else
                        requestPermissionInternet();}

            }
        });
    }

    private boolean checkPermissionInternet() {
        int networkStatePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE);
        int internetPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        return networkStatePermission == PackageManager.PERMISSION_GRANTED &&
                internetPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionInternet() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET},PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;

            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(MainActivity.this, "Puoi utilizzare la rete!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Permesso di utilizzo rete negato!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkPermission() {
        return Environment.isExternalStorageManager();
    }

    private void requestPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        requestPermissionLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {});

    private boolean copySelectedFiles(Uri[] selectedFileUris) throws FileNotFoundException {
        boolean success = true;
        String dstDirPath = "/sdcard/DCIM/SYNC";
        for (Uri srcFileUri : selectedFileUris) {
            // Ottieni il nome originale del file con estensione
            String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
            Cursor cursor = getContentResolver().query(srcFileUri, projection, null, null, null);
            String srcFileName = "";
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                srcFileName = cursor.getString(nameColumnIndex);
                ((Cursor) cursor).close();
            }

            String dstFilePath = dstDirPath + "/" + srcFileName;

            try {
                InputStream in = getContentResolver().openInputStream(srcFileUri);
                OutputStream out = new FileOutputStream(dstFilePath);

                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }

                in.close();
                out.close();

            } catch (Exception e) {
                e.printStackTrace();
                success = false;
                break;
            }
        }

        return success;
    }

    private boolean moveSelectedFiles(Uri[] selectedFileUris) throws FileNotFoundException {
        boolean success = true;
        String dstDirPath = "/sdcard/DCIM/SYNC";
        for (Uri srcFileUri : selectedFileUris) {
            // Ottieni il nome originale del file con estensione
            String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
            Cursor cursor = getContentResolver().query(srcFileUri, projection, null, null, null);
            String srcFileName = "";
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                srcFileName = cursor.getString(nameColumnIndex);
                ((Cursor) cursor).close();
            }

            String dstFilePath = dstDirPath + "/" + srcFileName;

            try {
                InputStream in = getContentResolver().openInputStream(srcFileUri);
                OutputStream out = new FileOutputStream(dstFilePath);

                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }

                //NON FUNZIONANO QUESTE 2 RIGHE DI CODICE!
                File originalFile = new File(String.valueOf(srcFileUri));
                originalFile.delete();

                in.close();
                out.close();

            } catch (Exception e) {
                e.printStackTrace();
                success = false;
                break;
            }
        }
        return success;
    }

    private final ActivityResultLauncher<String[]> filePickerLauncherCopy = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            selectedUris -> {
                if (selectedUris != null && !selectedUris.isEmpty()) {
                    Uri[] urisArray = selectedUris.toArray(new Uri[selectedUris.size()]);
                    boolean copyResult = false;
                    try {
                        copyResult = copySelectedFiles(urisArray);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    if (copyResult) {
                        Toast.makeText(MainActivity.this, "Copia eseguita con successo!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Errore, copia non riuscita!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> filePickerLauncherMove = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            selectedUris -> {
                if (selectedUris != null && !selectedUris.isEmpty()) {
                    Uri[] urisArray = selectedUris.toArray(new Uri[selectedUris.size()]);
                    boolean moveResult = false;
                    try {
                        moveResult = moveSelectedFiles(urisArray);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    if (moveResult) {
                        Toast.makeText(MainActivity.this, "Spostamento eseguito con successo!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Errore, spostamento non riuscito!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );



    private void copy() {
        String[] mimeTypes = {"*/*"}; // Consenti tutti i tipi di file
        boolean success = true;
        String dstDirPath = "/sdcard/DCIM/SYNC";
        // Verifica se la cartella "SYNC" esiste e, se necessario, la crea
        File syncDir = new File(dstDirPath);
        if (!syncDir.exists()) {
            boolean created = syncDir.mkdirs();
            if (!created) {
                Toast.makeText(MainActivity.this, "Impossibile creare la cartella SYNC", Toast.LENGTH_LONG).show();
                success = false;
            }
            else
                Toast.makeText(MainActivity.this, "Cartella SYNC creata con successo", Toast.LENGTH_LONG).show();

        }
        if(success)
            filePickerLauncherCopy.launch(mimeTypes);
    }

    private void move() {
        String[] mimeTypes = {"*/*"}; // Consenti tutti i tipi di file
        boolean success = true;
        String dstDirPath = "/sdcard/DCIM/SYNC";
        // Verifica se la cartella "SYNC" esiste e, se necessario, la crea
        File syncDir = new File(dstDirPath);
        if (!syncDir.exists()) {
            boolean created = syncDir.mkdirs();
            if (!created) {
                Toast.makeText(MainActivity.this, "Impossibile creare la cartella SYNC", Toast.LENGTH_LONG).show();
                success = false;
            }
            else
                Toast.makeText(MainActivity.this, "Cartella SYNC creata con successo", Toast.LENGTH_LONG).show();

        }
        if(success)
            filePickerLauncherMove.launch(mimeTypes);
    }

    private void showSmbCredentialsDialog() {
        String dstDirPath = "/sdcard/DCIM/SYNC";
        boolean successo = true;
        // Verifica se la cartella "SYNC" esiste e, se necessario, la crea
        File syncDir = new File(dstDirPath);
        if (!syncDir.exists()) {
            boolean created = syncDir.mkdirs();
            if (!created) {
                Toast.makeText(MainActivity.this, "Impossibile creare la cartella SYNC", Toast.LENGTH_LONG).show();
                successo=false;
            }
            else
                Toast.makeText(MainActivity.this, "Cartella SYNC creata con successo", Toast.LENGTH_LONG).show();

        }
        if(successo) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.dialog_smb_credentials, null);
            EditText usernameEditText = view.findViewById(R.id.usernameEditText);
            EditText passwordEditText = view.findViewById(R.id.passwordEditText);
            EditText smbUrlEditText = view.findViewById(R.id.smbUrlEditText);
            builder.setView(view)
                    .setPositiveButton("Avvia sync", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            String username = usernameEditText.getText().toString();
                            String password = passwordEditText.getText().toString();
                            String smbUrl = smbUrlEditText.getText().toString();
                            copyDirectoryToSMB(new java.io.File("/sdcard/DCIM/SYNC"), smbUrl, "BACKUP", username, password);
                        }
                    })
                    .setNegativeButton("Annulla", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Toast.makeText(MainActivity.this, "Sincronizzazione cancellata dall'utente!", Toast.LENGTH_LONG).show();
                        }
                    });
            builder.create().show();
        }
    }


    private void copyDirectoryToSMB(File localDir, String smbUrl, String shareName, String username, String password) {
        final boolean[] successo = {false};  // Variabile per tenere traccia se la sincronizzazione è andata a buon fine
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SMBClient client = new SMBClient();
                    try (Connection connection = client.connect(smbUrl)) {
                        AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), "");
                        Session session = connection.authenticate(ac);
                        // Connect to the specified share name
                        try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                            for (File localFile : localDir.listFiles()) {
                                if (localFile.isFile()) {
                                    try (FileInputStream in = new FileInputStream(localFile);
                                         com.hierynomus.smbj.share.File smbFile = share.openFile(localFile.getName(),
                                                 EnumSet.of(AccessMask.GENERIC_ALL),
                                                 null,
                                                 SMB2ShareAccess.ALL,
                                                 SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                                 null);
                                         OutputStream out = smbFile.getOutputStream()) {
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = in.read(buffer)) > 0) {
                                            out.write(buffer, 0, len);
                                        }

                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                if(successo[0]==false){
                                                Toast.makeText(MainActivity.this, "File SYNC sincronizzati col server!", Toast.LENGTH_SHORT).show();
                                                successo[0] =true;}
                                            }
                                        });

                                    } catch (Exception e) {
                                        // Handle exceptions
                                        e.printStackTrace();
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                Toast.makeText(MainActivity.this, "Errore, controlla la tua connessione e lo stato del server", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        return;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "Errore, controlla la tua connessione e lo stato del server!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
}