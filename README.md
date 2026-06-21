# Dance Competition Management System (DancingCompetition)

A comprehensive system designed for the automation, tracking, and analytics of dance competitions. The project integrates a relational database built on MS SQL Server with a client-facing Java application tailored for managing tournaments, judging protocols, and live pair leaderboards.

---

## Project Structure

The repository is organized into two isolated modules to ensure a clean separation of concerns:

* **`/DancingCompetition`** — Server-side logic, storage architecture, and SQL scripts (MS SQL Server).
* **`/java-application`** — Client/backend application written in Java for user and judge interaction.

---

## Database Architecture (`/DancingCompetition`)

The database storage layer is designed in strict compliance with the Third Normal Form (3NF) standards, enforcing robust referential integrity (cascading actions) and optimized for high-read performance.

### 📋 SQL Scripts Directory & Execution Order:

* **`SETUP.sql`** — Creates the `DancingCompetition` database and deploys the core 10 relational tables (`Country`, `Category`, `Dance`, `Competition`, `Participant`, `Judge`, `Pair`, `Pair_member`, `Performance`, `Score`).
* **`INTEGRITY_CONSTRAINTS.sql`** — Configures foreign key relationships (`ALTER TABLE ... FOREIGN KEY`) and defines declarative cascading rules (`ON DELETE CASCADE`).
* **`INDEXES.sql`** — Performance optimization layer: includes non-clustered search indexes (last names), unique constraints (`iso_code`), and **Covering Indexes (using `INCLUDE`)** to maximize analytical query speed.
* **`TRIGGERS.sql`** — Automated business logic enforcement via `INSTEAD OF INSERT, UPDATE` triggers on the performance tables to validate timeline boundaries and log errors into the `Auditlog` table.
* **`FUNCTIONS.sql`** — User-Defined Functions (UDFs) for dynamic calculations, featuring high-performance Inline Table-Valued Functions (Inline TVFs) for dance popularity analysis.
* **`PROCEDURES.sql`** — Stored procedures executing heavy background analytical reporting and global judge activity auditing.
* **`VIEW.sql`** — Database views utilizing advanced window functions (`DENSE_RANK`) to dynamically calculate real-time tournament leaderboards (e.g., extracting the `TOP-3` dancing pairs without rank gaps).
* **`INSERT.sql` / `UPDATE.sql` / `DELETE.sql`** — Data Manipulation Language (DML) scripts containing mock data and test cases for validating core system functionality.

---

## Key Optimization & Logic Highlights

* **Window Functions (`DENSE_RANK`):** The `dbo.vTopPairs` view dynamically calculates the top 3 highest-scoring pairs per competition based on average judge scores, handling dense tie-breaking seamlessly.
* **Transactional Integrity:** Strict business rules are managed directly at the database engine level, completely preventing performances from being scheduled outside the official start and end dates of a tournament.
* **Covering Indexes:** By utilizing the `INCLUDE(value)` clause on the `Score` table index, the database engine computes average metrics (`AVG`) directly from the index B-tree leaves without touching the underlying heavy data pages on the disk.

---

## Tech Stack

* **Database Engine:** MS SQL Server 2019/2022
* **Backend / Client:** Java (JDK 17+)
* **Database Connectivity:** JDBC / Hibernate ORM
* **IDEs Used:** IntelliJ IDEA / DataGrip

---

## 📺 Project Walkthrough & Demo (Video)

Click on the player preview below to watch a full video demonstration of the Java application operating and interacting in real-time with the MS SQL Server database:

[![Watch the video](https://www.youtube.com/watch?v=g5h16JCTSrw)

*(If the preview image does not display properly, you can use the direct link: [Watch Demo on YouTube](https://www.youtube.com/watch?v=g5h16JCTSrw))*
