package leondaval.SyncFiles;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        TextView hoCapito = findViewById(R.id.hoCapito);
        hoCapito.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Nascondi il bottone
                hoCapito.setVisibility(View.GONE);

                // Imposta la preferenza condivisa per indicare che l'app è stata avviata almeno una volta
                SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("firstStart", false);
                editor.apply();
                // Mancante controllo sul file prefs per vedere se app gia stata aperta

                // Passa alla MainActivity
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                finish();
            }
        });
    }
}