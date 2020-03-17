package com.example.reverseimagesearch
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment.getExternalStorageDirectory
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.util.Base64
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private val GALLERY = 1
    private val CAMERA = 2
    private val CLIENT_ID = "IMGUR API KEY HERE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSearch = findViewById<Button>(R.id.btnSearch)
        val btnOpenFiles = findViewById<Button>(R.id.btnOpenFiles)

        //making the search btn invalid until a picture is added
        btnSearch.isClickable = false

        btnOpenFiles!!.setOnClickListener {
            //opening picture option dialog
            pickPictureOptions()
            //allowing searching to begin
            btnSearch.isClickable = true
        }

    }

    //creating a dialog to pic between gallery and camera
    private fun pickPictureOptions() {
        val dia = AlertDialog.Builder(this)
        dia.setTitle("Select Action")
        val pictureDialogItems = arrayOf("Select photo from gallery", "Take a picture with camera")
        dia.setItems(pictureDialogItems) { dialog, which ->
            when (which) {
                0 -> startGallary()
                1 -> startCamera()
            }
        }
        dia.show()
    }

    //opens gallery
    private fun startGallary() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, GALLERY)
    }

    //opens camera
    private fun startCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra( MediaStore.EXTRA_SIZE_LIMIT, "720000")
        startActivityForResult(cameraIntent, CAMERA)
    }

    //adds image to ImageView and Uploads to Imgur. Also saves camera picture to gallery
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val contentURI = data!!.data
        if (requestCode == GALLERY) {
            if (data != null) {
                try {
                    val ivUploadedImage = findViewById<ImageView>(R.id.ivUploadedImage)
                    val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                    imageUpload(bitmap)
                    ivUploadedImage!!.setImageBitmap(bitmap)

                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Failed!", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == CAMERA) {
            val thumbnail = data!!.extras!!.get("data") as Bitmap
            ivUploadedImage!!.setImageBitmap(thumbnail)
            imageUpload(thumbnail)
            saveImage(thumbnail)
            Toast.makeText(this@MainActivity, "Image Saved!", Toast.LENGTH_SHORT).show()

        }
    }

    //opens a google search for the image usingt he imgur link
    private fun boom(imgURL: String) {
        var base_url: String = "https://www.google.com/searchbyimage?site=search&sa=X&image_url="
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(base_url+imgURL)))

    }

    //uploads image to imgur
    private fun imageUpload(image: Bitmap) {
        getBase64Image(image, complete = { base64Image ->
            GlobalScope.launch(Dispatchers.Default) {
                val url = URL("https://api.imgur.com/3/image")

                val boundary = "Boundary-${System.currentTimeMillis()}"

                val httpsURLConnection =
                    withContext(Dispatchers.IO) { url.openConnection() as HttpsURLConnection }
                httpsURLConnection.setRequestProperty("Authorization", "Client-ID $CLIENT_ID")
                httpsURLConnection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )

                httpsURLConnection.requestMethod = "POST"
                httpsURLConnection.doInput = true
                httpsURLConnection.doOutput = true

                var body = ""
                body += "--$boundary\r\n"
                body += "Content-Disposition:form-data; name=\"image\""
                body += "\r\n\r\n$base64Image\r\n"
                body += "--$boundary--\r\n"


                val outputStreamWriter = OutputStreamWriter(httpsURLConnection.outputStream)
                withContext(Dispatchers.IO) {
                    outputStreamWriter.write(body)
                    outputStreamWriter.flush()
                }

                //PARSING IMAGE for LINK
                val response = httpsURLConnection.inputStream.bufferedReader().use { it.readText() }  // defaults to UTF-8
                val jsonObject = JSONTokener(response).nextValue() as JSONObject
                val data = jsonObject.getJSONObject("data")
                Log.d("TAG", "Link is : ${data.getString("link")}")

                //Search button clicked
                btnSearch.setOnClickListener {
                    boom(data.getString("link"))
                }

            }
        })
    }

    //save image to gallery
    private fun saveImage(myBitmap: Bitmap):String {
        val bytes = ByteArrayOutputStream()
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
        val wallpaperDirectory = File(
            (getExternalStorageDirectory()).toString() + IMAGE_DIRECTORY)
        // have the object build the directory structure, if needed.
        Log.d("fee",wallpaperDirectory.toString())
        if (!wallpaperDirectory.exists()) {
            wallpaperDirectory.mkdirs()
        }

        try {
            Log.d("heel",wallpaperDirectory.toString())
            val f = File(wallpaperDirectory, ((Calendar.getInstance().timeInMillis).toString() + ".jpg"))
            f.createNewFile()
            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())
            MediaScannerConnection.scanFile(this,
                arrayOf(f.path),
                arrayOf("image/jpeg"), null)
            fo.close()
            Log.d("TAG", "File Saved::--->" + f.absolutePath)

            return f.absolutePath
        }
        catch (e1: IOException) {
            e1.printStackTrace()
        }

        return ""
    }

    companion object {
        private val IMAGE_DIRECTORY = "/reverseImageSearch"
    }

    //image format
    private fun getBase64Image(image: Bitmap, complete: (String) -> Unit) {
        GlobalScope.launch {
            val outputStream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val b = outputStream.toByteArray()
            complete(Base64.encodeToString(b, Base64.DEFAULT))

        }
    }

}