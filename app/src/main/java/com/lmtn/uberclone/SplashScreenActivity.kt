package com.lmtn.uberclone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FilterQueryProvider
import android.widget.ProgressBar
import android.widget.Toast
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import com.lmtn.uberclone.Common.DRIVER_INFO_REFERENCE
import com.lmtn.uberclone.model.DriverInfoModel
import com.lmtn.uberclone.utils.UserUtils
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.sql.Array
import java.sql.Time
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var progress_bar: ProgressBar;

    //Firebase Database
    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen();
    }

    override fun onStop() {
        if (firebaseAuth != null && listener != null) firebaseAuth.removeAuthStateListener(listener)
        super.onStop()
    }

    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe({
            firebaseAuth.addAuthStateListener(listener);
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        init()
    }

    private fun init() {

        progress_bar = findViewById(R.id.progress_bar);
        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)
        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                FirebaseInstanceId.getInstance().instanceId.addOnFailureListener { e ->
                    Toast.makeText(this@SplashScreenActivity, e.message, Toast.LENGTH_SHORT).show()
                }
                    .addOnSuccessListener { instanceIdResult ->
                        Log.d("TOKEN", instanceIdResult.token)
                        UserUtils.updateToken(this@SplashScreenActivity, instanceIdResult.token)
                    }
                checkUserFromFirebase();
            } else
                showLoginLayout()
        }
    }

    private fun checkUserFromFirebase() {
        driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, p0.message, Toast.LENGTH_SHORT).show()
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
//                        Toast.makeText(
//                            this@SplashScreenActivity,
//                            "User already register",
//                            Toast.LENGTH_SHORT
//                        ).show()
                        val model = dataSnapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout();
                    }
                }
            })
    }

    private fun goToHomeActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        startActivity(Intent(this, DriverHomeActivity::class.java))
        finish()
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemInflater = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val edtFirstName = itemInflater.findViewById<View>(R.id.edt_first_name) as TextInputEditText
        val edtLastName = itemInflater.findViewById<View>(R.id.edt_last_name) as TextInputEditText
        val edtPhoneNumber =
            itemInflater.findViewById<View>(R.id.edt_phone_number) as TextInputEditText

        val btnContinue = itemInflater.findViewById<View>(R.id.btn_register) as Button

        //Set Data
        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null &&
            !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        )
            edtPhoneNumber.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)

        //Set View
        builder.setView(itemInflater)
        val dialog = builder.create()
        dialog.show()

        //Event
        btnContinue.setOnClickListener {
            if (TextUtils.isDigitsOnly(edtFirstName.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter first name",
                    Toast.LENGTH_SHORT
                ).show();
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(edtLastName.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter last name",
                    Toast.LENGTH_SHORT
                ).show();
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(edtPhoneNumber.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter phone name",
                    Toast.LENGTH_SHORT
                ).show();
                return@setOnClickListener
            } else {
                val model = DriverInfoModel()
                model.fistName = edtFirstName.text.toString()
                model.lastName = edtLastName.text.toString()
                model.phoneNumber = edtPhoneNumber.text.toString()
                model.rating = 0.0

                driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "" + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        progress_bar.visibility = View.GONE
                    }
                    .addOnSuccessListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "Register Successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        goToHomeActivity(model)
                        progress_bar.visibility = View.GONE

                    }
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance().createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build()
            , LOGIN_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else
                Toast.makeText(
                    this@SplashScreenActivity,
                    "" + response!!.error!!.message,
                    Toast.LENGTH_SHORT
                ).show();
        }
    }
}