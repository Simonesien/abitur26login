# Login der abitur26.de Webseite
Der für den Login relevante Teil des Source Codes für die abitur26.de Webseite ist hier für eine bessere Transparenz veröffentlicht.

HTTPS_Server.java läuft im Backend, während login.html die Frontend-Oberfläche ist. Frontend und Backend kommunizieren über eine API. Wenn die Authentisierung erfolgreich ist, leitet login.html auf intern/index.html weiter, welche ausschließlich mit gültigem Session-Cookie erreichbar ist. login.html hat also die Aufgabe, durch Eingabe korrekter Anmeldedaten diesen Session-Cookie vom Backend zu bekommen.

## Diagramme
Um den Backend-Code besser erschließen zu können, sind die beiden Diagramme FlussdiagrammLoginBackend.png und UMLDiagrammBackend.png beigefügt, da sie sicherlich nützen können.

## Vollständigkeit
Hier ist nur der Teil des Source Codes, der für den Login zuständig ist, publiziert. Alles andere (hauptsächlich die anschließende Verifizierung der Gültigkeit des erlangten Session-Cookies sowie der Source Code des internen Bereichs auf der Webseite, auf welchen man ohne Authentisierung keinen Zugriff), ist hier nicht veröffentlicht, weil dessen Veröffentlichung den Zweck überschreiten würde, die Eingabe der sensiblen Anmeldedaten durch den Loginprozess zu verfolgen.

## Code testen
Um den Code auszuführen, ist es nötig, die beiden Bibliotheken "org.json.jar" und "sqlite.jar" einzubinden, die aus Lizenzgründen hier nicht extra veröffentlicht sind. Dann kann man das Backend mit dem Command ``` java -cp ".:org.json.jar:sqlite.jar" HTTPS_Server.java ``` starten. (Auf Windows ; statt : als Separator im Path)
## Aktualität
Die Veröffentlichung ist derzeit auf dem Stand vom 31.8.2025. Es kann natürlich sein, dass nach diesem Datum noch Änderungen an der Webseite, unter anderem also auch an login.html gibt. Zum Beispiel können die Fehler in den Fehlermeldungen spezifischer beschrieben oder das Design der Webseite geändert werden. In diesem Fall werden die Änderungen hier nicht entsprechend aktualisiert. Sollte sich jedoch im wesentlichen Loginprozess etwas ändern, werden die Neuerungen hier veröffentlicht.

## Lizenzanmerkungen
Dieser Code dient nur zu Transparenzgründen und darf nicht weiterverwendet oder woanders publiziert werden. Auch darf nicht auf Grundlage dieses Codes nach Schwachstellen in der Software gesucht werden.
