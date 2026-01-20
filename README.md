# SplitManager
Sistema software per la gestione delle spese condivise all'interno di gruppi di persone.

## Funzionalità
- Gestione gruppi e membri
- Tracciamento spese condivise
- Calcolo automatico saldi
- Ottimizzazione rimborsi
- Sistema di notifiche


## Autori
Carmen Possidente - 7115970

Matteo Gerlotti -  7025024 

Roberta Donato -  7113502 


## Architettura
Il progetto segue un'architettura **Multilayered** che garantisce una chiara separazione delle responsabilità:
- **Presentation Layer**: Interfaccia a riga di comando (CLI) per l'interazione con l'utente.
- **Business Logic Layer**: Gestione della logica applicativa e coordinamento delle operazioni (Service Layer).
- **Domain Model Layer**: Entità del dominio (es. User, Expense, Balance) e regole di validazione.
- **Persistence Layer**: Pattern DAO implementato con **JDBC** su database **H2**.


## Tecnologie Utilizzate
- **Linguaggio**: Java 23
- **Database**: H2 Database
- **Build System:** Maven
- **Database Connectivity**: JDBC
- **Testing**: JUnit 5
- **Diagrammi**: Lucidchart
- **IDE**: IntelliJ IDEA

## Documentazione
La documentazione completa del progetto è disponibile nel file Relazione.pdf

