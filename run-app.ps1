# Silent launcher - no credentials displayed, hidden window
$ErrorActionPreference = "SilentlyContinue"

# Change to script directory
Set-Location $PSScriptRoot

# Check Java silently
$javaCheck = & java -version 2>&1
if ($LASTEXITCODE -ne 0) {
    [System.Windows.Forms.MessageBox]::Show("ERROR: Java not found! Please install Java JDK 17+", "Error", "OK", "Error")
    exit 1
}

# Check Maven silently
$mavenCheck = & mvn -version 2>&1
if ($LASTEXITCODE -ne 0) {
    [System.Windows.Forms.MessageBox]::Show("ERROR: Maven not found! Please install Maven", "Error", "OK", "Error")
    exit 1
}

# Launch application in background (hidden window)
$process = Start-Process -FilePath "mvn" -ArgumentList "javafx:run" -WindowStyle Hidden -PassThru

# Exit immediately
exit 0
