package leondaval.SyncFiles;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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

    ExecutorService executorService = Executors.newSingleThreadExecutor(); // Esecuzione del processo su thread separato (per non intasare la memoria e il thread principale)
    private static final int PERMISSION_REQUEST_CODE_MEMORY = 1;  // ID per la richiesta del permesso relativo all'accesso alla memoria
    private static final int PERMISSION_REQUEST_CODE_INTERNET = 2;  // ID per la richiesta del permesso relativo all'uso di internet per il collegamento al server SMB
    boolean permissionCheck = false; //Controllo stato dei permessi
    private AlertDialog progressDialog; // Popup a schermo che mostra lo il caricamento del processo corrente
    private boolean isCopying = false; // Variabile per tenere traccia dello stato del processo di copia
    private boolean isMoveing = false; // Variabile per tenere traccia dello stato del processo di spostamento

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissionMemoryAll();

        if (checkPermissionMemory()) { // Verifica se il permesso è già stato concesso durante un esecuzione dell'app in passato

            Toast.makeText(MainActivity.this, "Permesso necessario, già concesso, complimenti!", Toast.LENGTH_SHORT).show();
            permissionCheck = true;

        }

        Button copyDirectoryButton = findViewById(R.id.copyFilesButton);
        copyDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermissionMemory() || permissionCheck){

                    // Inizializza e mostra l'AlertDialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setView(R.layout.progress_dialog_layout); // Creare un layout personalizzato con una ProgressBar
                    builder.setCancelable(false); // Imposta su true se vuoi che l'utente possa annullare l'operazione

                    progressDialog = builder.create();
                    progressDialog.show();

                    isCopying = true; // Imposta la variabile a true quando inizia il processo di copia

                    executeInBackground(() -> {
                        copy();
                    });

                    isCopying = false; // Reimposta la variabile a false quando il processo di copia è completato
                    progressDialog.dismiss(); // Chiudi l'AlertDialog

                }else
                    requestPermissionMemory();
            }
        });

        Button moveDirectoryButton = findViewById(R.id.moveFilesButton);
        moveDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermissionMemory() || permissionCheck) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setView(R.layout.progress_dialog_layout); // Creare un layout personalizzato con una ProgressBar
                    builder.setCancelable(false); // Imposta su true se vuoi che l'utente possa annullare l'operazione

                    progressDialog = builder.create();
                    progressDialog.show();

                    isMoveing = true; // Imposta la variabile a true quando inizia il processo di spostamento

                    executeInBackground(() -> {
                        move();
                    });

                    isMoveing = false; // Reimposta la variabile a false quando il processo di spostamento è completato
                    progressDialog.dismiss(); // Chiudi l'AlertDialog

                }else
                    requestPermissionMemory();
            }
        });

        Button syncDirectoryButton = findViewById(R.id.syncFilesButton);
        syncDirectoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (checkPermissionMemory() && checkPermissionInternet())
                    showSmbCredentialsDialog();

                else {

                    if (!checkPermissionMemory())
                        requestPermissionMemory();

                    else
                        requestPermissionInternet();
                }

            }
        });

    }

    // Mostra nuovamente il ProgressDialog quando l'Activity viene riportata in primo piano
    @Override
    protected void onResume() {
        super.onResume();
        if (progressDialog != null && (isCopying || isMoveing) && !progressDialog.isShowing())
            progressDialog.show();
    }

    // Nasconde il ProgressDialog quando l'Activity va in background
    @Override
    protected void onPause() {
        super.onPause();
        if (progressDialog != null && (isCopying || isMoveing) && progressDialog.isShowing())
            progressDialog.dismiss();
    }

    private void executeInBackground(Runnable task) {executorService.execute(task);}

    private boolean checkPermissionInternet() {
        int networkStatePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE);
        int internetPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        return networkStatePermission == PackageManager.PERMISSION_GRANTED &&
                internetPermission == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkPermissionMemory() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionMemory() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, PERMISSION_REQUEST_CODE_MEMORY);
    }

    private void requestPermissionMemoryAll() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        requestPermissionLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {});

    private void requestPermissionInternet() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET}, PERMISSION_REQUEST_CODE_INTERNET);
    }

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

    private boolean move() {
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
        return success;
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