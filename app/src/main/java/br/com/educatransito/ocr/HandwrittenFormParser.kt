package br.com.educatransito.ocr
import java.text.Normalizer
import java.util.Locale
data class FormOcrSuggestion(val name:String="",val cpf:String="",val phone:String="",val plate:String="",val audience:String="",val motive:String="",val subject:String="",val guidance:String="",val rawText:String="")
object HandwrittenFormParser {
 fun parse(raw:String):FormOcrSuggestion {
  val lines=raw.replace('\r','\n').lines().map(String::trim).filter(String::isNotEmpty)
  fun value(label:String)=lines.firstNotNullOfOrNull { Regex("^"+label+"\\s*[:：-]?\\s*(.*)$",RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()
  fun norm(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
  val cpf=Regex("(?<!\\d)(\\d{3}[. ]?\\d{3}[. ]?\\d{3}[- ]?\\d{2})(?!\\d)").find(raw)?.groupValues?.get(1)?.filter(Char::isDigit).orEmpty().takeIf{it.length==11}.orEmpty()
  val plate=Regex("(?i)(?<![A-Z0-9])([A-Z]{3}[- ]?\\d[A-Z0-9]\\d{2})(?![A-Z0-9])").find(raw)?.groupValues?.get(1)?.filter(Char::isLetterOrDigit)?.uppercase(Locale.ROOT).orEmpty()
  val phone=Regex("(?<!\\d)(\\(?\\d{2}\\)?\\s?9?\\d{4}[- ]?\\d{4})(?!\\d)").find(raw)?.groupValues?.get(1).orEmpty()
  val n=norm(raw)
  val audience=when { n.contains("carro") -> "Motorista"; n.contains("moto") -> "Motociclista"; n.contains("pedestre") -> "Pedestre"; n.contains("ciclista") || n.contains("bicicleta eletrica") -> "Ciclista"; else -> "" }
  val motive=if(n.contains("motivada")) "Motivada" else if(n.contains("rotina")) "Rotina" else ""
  val i=lines.indexOfFirst{norm(it).startsWith("orientacoes")}
  val guidance=if(i>=0) (listOf(lines[i].substringAfter(':',""))+lines.drop(i+1).takeWhile{!norm(it).startsWith("obs")}).filter(String::isNotBlank).joinToString("\n") else ""
  return FormOcrSuggestion(value("Nome"),cpf,phone,plate,audience,motive,value("Assunto"),guidance,raw)
 }
}
