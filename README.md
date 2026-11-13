# AziendePlugin

Plugin per server Minecraft Paper/Spigot (API Paper 1.20.4, Java 17). Le aziende e i dipendenti sono salvati in `plugins/AziendePlugin/aziende.yml`.

## Compilazione e installazione

```bash
mvn package
```

Copia `target/aziendeplugin-1.0.0.jar` nella cartella `plugins` e riavvia il server. I comandi amministrativi sono disponibili agli operatori tramite il permesso `aziende.admin`.

## Comandi

- `/aziendeadmin create <nome>` crea un'azienda.
- `/aziendeadmin elimina <nome>` mostra i pulsanti cliccabili di conferma/rifiuto.
- `/aziendeadmin pex <nick> <azienda>` assegna il direttore.
- `/aziendeadmin info <azienda>` mostra fondo, dipendenti, direttore e vicedirettore.
- `/azienda assumi <nick> <grado>` invia un contratto cliccabile al giocatore online entro 7 blocchi. Il grado deve prima esistere.
- `/azienda licenzia <nick>` rimuove un dipendente anche se offline.
- `/azienda info` mostra le informazioni della propria azienda.
- `/azienda rango aggiungi <grado>` crea un grado.
- `/azienda rango modifica <nick> <grado>` oppure `/azienda rango <grado> <nick>` assegna/modifica un grado.
- `/azienda accetta` e `/azienda rifiuta` gestiscono il contratto (normalmente si usano i pulsanti in chat).

`ViceDirettore` ha i permessi di direttore per assunzioni, licenziamenti e gestione dei gradi. Il fondo viene inizializzato a 0 e mostrato nelle informazioni; in questa versione non sono previsti comandi economici per modificarlo. I contratti pendenti durano fino allo spegnimento/riavvio del server.
