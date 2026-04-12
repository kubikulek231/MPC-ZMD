# ZMD-cv1

## Requirements

- **Java SDK 25 SE** — must be installed and available on the system `PATH`
- **Apache Maven** — must be installed and available on the system `PATH`
- **VS Code** with the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) (Microsoft)

### Verify prerequisites (PowerShell)

```powershell
java -version   # should report: openjdk 25 ...
mvn -version    # should report: Apache Maven ...
```

---

## Running from the Terminal (Maven)

### 1. Resolve dependencies (first time only)

```powershell
mvn dependency:resolve
```

### 2. Run the application

```powershell
mvn javafx:run
```

---

## Running from VS Code (Microsoft Java Extension)

### 1. Install the Extension Pack for Java

Open the Extensions view (`Ctrl+Shift+X`), search for **Extension Pack for Java** by Microsoft and install it.

### 2. Open the project

Open the `ZMD-cv1` folder in VS Code (`File > Open Folder...`).  
The Java extension will automatically detect the Maven project and resolve the classpath.

### 3. Configure the JDK

The project is configured to use whichever `java` is on your `PATH`.  
To confirm VS Code picks it up, open the Command Palette (`Ctrl+Shift+P`) and run:

```
Java: Configure Java Runtime
```

Make sure **JavaSE-25** is listed under *Project JDKs*. If not, point it to your JDK installation directory.

### 4. Run the application

- Press **F5** (or go to *Run > Start Debugging*) to launch the `Launch App` configuration.
- Alternatively, open `src/main/java/app/Main.java` and click the **Run** code lens that appears above the `main` method.
