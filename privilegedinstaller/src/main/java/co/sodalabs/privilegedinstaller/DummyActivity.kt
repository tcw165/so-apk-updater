package co.sodalabs.privilegedinstaller

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DummyActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        Toast.makeText(this, "Hey developers, the privileged installer has no UI so the launcher Activity will just finish.", Toast.LENGTH_LONG).show()
        finish()
    }
}