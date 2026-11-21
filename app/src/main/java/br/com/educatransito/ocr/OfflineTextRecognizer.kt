package br.com.educatransito.ocr
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
class OfflineTextRecognizer {
 suspend fun recognize(context:Context,uri:Uri):String=suspendCancellableCoroutine { c ->
  val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  c.invokeOnCancellation{recognizer.close()}
  try { recognizer.process(InputImage.fromFilePath(context,uri))
   .addOnSuccessListener{if(c.isActive)c.resume(it.text);recognizer.close()}
   .addOnFailureListener{if(c.isActive)c.resumeWithException(it);recognizer.close()}
  } catch(e:Exception){recognizer.close();if(c.isActive)c.resumeWithException(e)}
 }
}
