# Verificadores

`StaticCompatibility.java` analiza el JAR final y resuelve los miembros usados
por la expansión sin inicializar Bukkit. `ModelSmoke.java` ejecuta el código
real de la expansión con servidor, inventario, jugador y servicio de placeholders
simulados. Conserva las APIs reales de Paper, las clases reales de NamespacedKey
y la superclase real de PlaceholderAPI 2.12.3. No equivale a una prueba en un servidor.

Los registros incluidos corresponden al JAR entregado, NBT-API 2.16.1,
Paper 26.2 build 132 y Paper 26.3 build 159 BETA.

Para repetirlos, compila primero con `mvn clean package`. Después ejecuta
`Run-Checks.ps1` con estos parámetros:

- `PaperLibraries`: carpeta con todos los JAR de `META-INF/libraries/` extraídos
  del bundle oficial de Paper para la versión correspondiente. Incluye ASM y
  Adventure. No mezcles las APIs de dos versiones de Paper en esta carpeta.
- `PlaceholderApi`: JAR de API 2.12.3 de repo.helpch.at.
- `Annotations`: JAR de org.jetbrains:annotations:26.0.2.
- `Guava`: JAR de com.google.guava:guava:33.6.0-jre.
- `NbtApi`: JAR original de de.tr7zw:item-nbt-api:2.16.1, para recompilar el fuente.
- `BukkitVersion`: `26.2-R0.1-SNAPSHOT` o `26.3-R0.1-SNAPSHOT`.
- `ExpansionJar`: opcional; por defecto `../target/Expansion-CheckItem.jar`.

Los JAR de las dependencias y del servidor no se incluyen en este ZIP.
Se requiere JDK 25 y PowerShell. Los archivos auxiliares se guardan en
`verification/work/`, fuera del directorio de fuentes del build Maven.

Checksums oficiales de los bundles usados:

```text
paper-26.2-132.jar
5ab560a769c1ab413cb7f637dd0dc697974571f2db0a667cfbac511422e51b26

paper-26.3-159.jar
2224a0b2b6b096ff4c429ad926e97977213e4f633e90cb3a49b5eeb82f94bab0
```
