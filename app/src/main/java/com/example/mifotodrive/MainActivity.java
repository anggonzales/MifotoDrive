package com.example.mifotodrive;

import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.content.CursorLoader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;


import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public TextView mDisplay;
    static Drive servicio = null;
    static GoogleAccountCredential credencial = null;
    static String nombreCuenta = null;
    static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static Handler manejador = new Handler();
    private static Handler carga = new Handler();
    private static ProgressDialog dialogo;
    private Boolean noAutoriza = false;
    SharedPreferences prefs;
    SharedPreferences.Editor editor;
    static final int SOLICITUD_SELECCION_CUENTA = 1;
    static final int SOLICITUD_AUTORIZACION = 2;
    static final int SOLICITUD_SELECCIONAR_FOTOGRAFIA = 3;
    static final int SOLICITUD_HACER_FOTOGRAFIA = 4;
    private static Uri uriFichero;
    private String idCarpeta = "";
    static final String DISPLAY_MESSAGE_ACTION = "com.example.mifotodrive.DISPLAY_MESSAGE";

    //para el recycler de imagenes
    private ImageView imageView;
    RecyclerView recyclerView;
    GridLayoutManager gridLayoutManager;
    ArrayList<ImageURL> imageUrlList;
    DataAdapter dataAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        registerReceiver(mHandleMessageReceiver, new
                IntentFilter(DISPLAY_MESSAGE_ACTION));
        mDisplay = (TextView) findViewById(R.id.txtDisplay);
        credencial = GoogleAccountCredential.usingOAuth2(this,
                Arrays.asList(DriveScopes.DRIVE));
        prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
        nombreCuenta = prefs.getString("nombreCuenta", null);
        noAutoriza = prefs.getBoolean("noAutoriza", false);
        if (!noAutoriza) {
            if (nombreCuenta == null) {
                PedirCredenciales();
            } else {
                credencial.setSelectedAccountName(nombreCuenta);
                servicio = obtenerServicioDrive(credencial);
                if (idCarpeta != null) {
                    listarFicheros(this.findViewById(android.R.id.content));
                }
            }
        }

        idCarpeta = prefs.getString("idCarpeta", null);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        imageUrlList=new ArrayList<>();
        imageView = (ImageView) findViewById(R.id.imageView);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        gridLayoutManager = new GridLayoutManager(getApplicationContext(), 2);
        recyclerView.setLayoutManager(gridLayoutManager);
        dataAdapter = new DataAdapter(getApplicationContext(), imageUrlList);
        recyclerView.setAdapter(dataAdapter);
    }

    private void PedirCredenciales() {
        if (nombreCuenta == null) {
            startActivityForResult(credencial.newChooseAccountIntent(),
                    SOLICITUD_SELECCION_CUENTA);
        }
    }


    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case SOLICITUD_SELECCION_CUENTA:
                if (resultCode == RESULT_OK && data != null
                        && data.getExtras() != null) {
                    nombreCuenta = data
                            .getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (nombreCuenta != null) {
                        credencial.setSelectedAccountName(nombreCuenta);
                        servicio = obtenerServicioDrive(credencial);
                        editor = prefs.edit();
                        editor.putString("nombreCuenta", nombreCuenta);
                        editor.commit();
                        crearCarpetaEnDrive(nombreCuenta);
                    }
                }
                break;
            case SOLICITUD_HACER_FOTOGRAFIA:
                if (resultCode == Activity.RESULT_OK) {
                    guardarFicheroEnDrive(this.findViewById(android.R.id.content));
                }
                break;
            case SOLICITUD_SELECCIONAR_FOTOGRAFIA:
                if (resultCode == Activity.RESULT_OK) {
                    Uri ficheroSeleccionado = data.getData();
                    String[] proyeccion = {MediaStore.Images.Media.DATA};
                    Cursor cursor = new CursorLoader(getApplicationContext(), ficheroSeleccionado, proyeccion, null, null, null).loadInBackground();
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();
                    uriFichero = Uri.fromFile(new java.io.File(cursor.getString(column_index)));
                    guardarFicheroEnDrive(this.findViewById(android.R.id.content));
                }
                break;
            case SOLICITUD_AUTORIZACION:
                if (resultCode == Activity.RESULT_OK) {
                    crearCarpetaEnDrive(nombreCuenta);
                } else {
                    noAutoriza = true;
                    editor = prefs.edit();
                    editor.putBoolean("noAutoriza", true);
                    editor.commit();
                    mostrarMensaje(this, "El usuario no autoriza usar Google Drive");
                }
                break;
        }
    }

    private void crearCarpetaEnDrive(final String nombreCarpeta) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mostrarCarga(MainActivity.this, "Creando carpeta..."); //Crear carpeta EventosDrive
                    if (idCarpeta == null) {
                        File metadataFichero = new File();
                        metadataFichero.setName(nombreCarpeta + " " + " fotodrive2020");
                        metadataFichero.setMimeType("application/vnd.google-apps.folder");
                        metadataFichero.setParents(Collections.singletonList("1jr3grGhKkkraUEjyPRSXihqH0-TsvLKr"));
                        File fichero = servicio.files().create(metadataFichero).setFields("id").execute();
                        if (fichero.getId() != null) {
                            editor = prefs.edit();
                            editor.putString("idCarpeta", fichero.getId());
                            editor.commit();
                            idCarpeta = fichero.getId();
                            mostrarMensaje(MainActivity.this, "¡Carpeta creada!");
                        }
                    }
                    ocultarCarga(MainActivity.this);
                } catch (UserRecoverableAuthIOException e) {
                    ocultarCarga(MainActivity.this);
                    startActivityForResult(e.getIntent(), SOLICITUD_AUTORIZACION);
                } catch (IOException e) {
                    mostrarMensaje(MainActivity.this, "Error;" + e.getMessage());
                    ocultarCarga(MainActivity.this);
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    static void mostrarMensaje(final Context context, final String mensaje) {
        manejador.post(new Runnable() {
            public void run() {
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static void mostrarCarga(final Context context, final String mensaje) {
        carga.post(new Runnable() {
            public void run() {
                dialogo = new ProgressDialog(context);
                dialogo.setMessage(mensaje);
                dialogo.show();
            }
        });
    }

    static void ocultarCarga(final Context context) {
        carga.post(new Runnable() {
            public void run() {
                dialogo.dismiss();
            }
        });
    }

    private Drive obtenerServicioDrive(GoogleAccountCredential credencial) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                new GsonFactory(), credencial).build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        View vista = (View) findViewById(android.R.id.content);
        int id = item.getItemId();
        switch (id) {
            case R.id.action_camara:
                if (!noAutoriza) {
                    hacerFoto(vista);
                }
                break;
            case R.id.action_galeria:
                if (!noAutoriza) {
                    seleccionarFoto(vista);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    public void hacerFoto(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this, "Debes seleccionar una cuenta de Google Drive");
        } else {
            String mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(new Date());
            uriFichero = Uri.fromFile(new java.io.File(mediaStorageDir + java.io.File.separator + "IMG_" + timeStamp + ".jpg"));
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriFichero);
            startActivityForResult(cameraIntent, SOLICITUD_HACER_FOTOGRAFIA);
        }
    }

    public void seleccionarFoto(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this, "Debes seleccionar una cuenta de Google Drive");
        } else {
            Intent seleccionFotografiaIntent = new Intent();
            seleccionFotografiaIntent.setType("image/*");
            seleccionFotografiaIntent.setAction(Intent.ACTION_PICK);
            startActivityForResult(Intent.createChooser(seleccionFotografiaIntent,
                    "Seleccionar fotografía"), SOLICITUD_SELECCIONAR_FOTOGRAFIA);
        }
    }

    private void guardarFicheroEnDrive(final View view) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mostrarCarga(MainActivity.this, "Subiendo imagen...");
                    java.io.File ficheroJava = new java.io.File(uriFichero.getPath());
                    FileContent contenido = new FileContent("image/jpeg", ficheroJava);
                    File ficheroDrive = new File();
                    ficheroDrive.setName(ficheroJava.getName());
                    ficheroDrive.setMimeType("image/jpeg");
                    ficheroDrive.setParents(Collections.singletonList(idCarpeta));
                    File ficheroSubido = servicio.files().create(ficheroDrive, contenido).setFields("id").execute();
                    if (ficheroSubido.getId() != null) {
                        mostrarMensaje(MainActivity.this, "¡Foto subida!");
                        listarFicheros(view);
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                dataAdapter.update(imageUrlList);
                            }
                        });
                    }
                    ocultarCarga(MainActivity.this);
                } catch (UserRecoverableAuthIOException e) {
                    ocultarCarga(MainActivity.this);
                    startActivityForResult(e.getIntent(), SOLICITUD_AUTORIZACION);
                } catch (IOException e) {
                    mostrarMensaje(MainActivity.this, "Error;" + e.getMessage());
                    ocultarCarga(MainActivity.this);
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String nuevoMensaje = intent.getExtras().getString("mensaje");
            mDisplay.append(nuevoMensaje + "\n");
            mDisplay.setText(nuevoMensaje);
        }
    };

    static void mostrarTexto(Context contexto, String mensaje) {
        Intent intent = new Intent(DISPLAY_MESSAGE_ACTION);
        intent.putExtra("mensaje", mensaje);
        contexto.sendBroadcast(intent);
    }

    public void listarFicheros(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this,
                    "Debes seleccionar una cuenta de Google Drive");
        } else {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    /*try {
                        mostrarCarga(MainActivity.this, "Listando archivos...");
                        FileList ficheros = servicio.files().list()
                                .setQ("'" + idCarpeta + "' in parents")
                                .setFields("*")
                                .execute();
                        for (File fichero : ficheros.getFiles()) {
                            mostrarTexto(getBaseContext(), fichero.getOriginalFilename());
                            Log.i("listando","id:"+fichero.getId());
                        }
                        mostrarMensaje(MainActivity.this,
                                "¡Archivos listados!");
                        ocultarCarga(MainActivity.this);
                    }*/
                    try {
                        mostrarCarga(MainActivity.this, "Listando archivos...");
                        FileList ficheros = servicio.files().list()
                                .setQ("'" + idCarpeta + "' in parents")
                                .setFields("*")
                                .execute();
                        imageUrlList.clear();
                        String mimensaje="";
                        for (File fichero : ficheros.getFiles()) {
                            mimensaje=mimensaje+fichero.getOriginalFilename() + "\n";
                            Log.i("listando","id:"+fichero.getId());
                            ImageURL imageUrl = new ImageURL("https://drive.google.com/uc?export=download&id="+fichero.getId());
                            imageUrlList.add(imageUrl);
                        }
                        mostrarTexto(getBaseContext(), mimensaje);
                        mostrarMensaje(MainActivity.this,
                                "¡Archivos listados!");
                        ocultarCarga(MainActivity.this);
                    } catch (UserRecoverableAuthIOException e) {
                        ocultarCarga(MainActivity.this);
                        startActivityForResult(e.getIntent(),
                                SOLICITUD_AUTORIZACION);
                    } catch (IOException e) {
                        mostrarMensaje(MainActivity.this,
                                "Error;" + e.getMessage());
                        ocultarCarga(MainActivity.this);
                        e.printStackTrace();
                    }
                }
            });
            t.start();
        }
    }

}
