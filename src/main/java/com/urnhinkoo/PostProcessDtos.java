package com.urnhinkoo;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PostProcessDtos {

    private final static String TAB = "    ";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Debe proporcionar la ruta de la carpeta 'domain/model' como argumento.");
            String folder1 = "C:\\Users\\plamunof\\git\\wks\\urnhinkoo\\src\\main\\java\\com\\urnhinkoo\\domain\\model";
            args = new String[]{folder1};
        }
        //Primero limpiamos la carpeta domain.
        String ruta = args[0];
        String projectPath = ruta.replace("\\src\\main\\java\\com\\urnhinkoo\\domain\\model", "");

        Path folder = Paths.get(ruta);
        Path correctPath = folder.getParent();
        if (correctPath.toFile().exists()) {
            FileUtils.deleteDirectory(correctPath.toFile());
        }
        //Ahora movemos los generados a la carpeta correcta
        Path incorrectPath = Paths.get(projectPath.concat("\\com.urnhinkoo.domain\\src\\main\\java\\com\\urnhinkoo\\domain"));
        if (incorrectPath.toFile().exists()) {
            FileUtils.moveDirectory(incorrectPath.toFile(), correctPath.toFile());
        } else {
            System.err.println("Las clases autogeneradas no existen.");
        }
        //Dar formato
        // Ya tenemos el path de antes
        System.out.println("Processing folder: " + folder.toAbsolutePath());
        if (folder.toFile().exists()) {
            try (var paths = Files.walk(folder)) {
                paths.filter(f -> f.toString().endsWith(".java")).forEach(PostProcessDtos::processFile);
            }
        } else {
            System.err.println("La carpeta domain no existe, deberia de haber sido copiada en un paso previo");
        }

        // Eliminamos la carpeta incorrecta
        Path incorrectParent = Paths.get(projectPath.concat("\\com.urnhinkoo.domain"));
        if (incorrectParent.toFile().exists() && incorrectParent.toFile().isDirectory()) {
            System.out.println("Folder to delete: " + incorrectParent.getFileName());
            FileUtils.deleteDirectory(incorrectParent.toFile());
        } else {
            System.err.println("Origin Domain folder doenst exists");
        }
    }

    private static void processFile(Path file) {
        try {
            Context context = new Context();
            if (Files.isDirectory(file)) return;
            String[] lines = Files.readAllLines(file).toArray(new String[0]);
            StringBuilder sb = new StringBuilder();
            context.depth = 0;

            for (String line : lines) {
                try {

                    line = line.trim();
                    // Si la línea es '}', disminuimos la profundidad antes de procesarla
                    if (line.equals("}")) context.depth = Math.max(0, context.depth - 1);


                    // Procesamos la línea según su contenido
                    processLine(line, sb, context);

                    // Si la línea es '{', aumentamos la profundidad después de procesarla
                    if (line.contains("{")) context.depth++;
                } catch (Exception e) {
                    System.err.println("Error processing line " + line);
                }
            }

            Files.write(file, sb.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file: " + file);
        }
    }


    private static void processLine(String line, StringBuilder sb, Context context) {
        String trimmed = line.trim();


        if (context.insideEnum) {
            if (!trimmed.isEmpty()) formatEnumLine(trimmed, sb, context);
            return;
        }

        if (trimmed.isEmpty() && (context.lastLineEmpty || context.lastLineNotation)) {
            return; // Saltar líneas vacías consecutivas
        }

        if (trimmed.isEmpty()) {
            sb.append(System.lineSeparator());
            context.lastLineEmpty = true;
            context.lastLineNotation = false;
            return;
        }

        if (trimmed.startsWith("@")) {
            processAnnotationsLine(trimmed, sb, context);
        } else {
            processNormalLine(trimmed, sb, context);
        }

        context.lastLineEmpty = false;

    }


    private static void formatEnumLine(String line, StringBuilder sb, Context context) {
        if (hasMultipleEnum(line)) {
            gestionarMutlipleEnums(line, sb, context);
        } else {
            sb.append(TAB.repeat(context.depth)).append(line).append(System.lineSeparator());
        }


        // cierra modo enum cuando llega el final con ;
        if (line.endsWith(";")) context.insideEnum = false;
    }


    private static boolean hasMultipleEnum(String line) {
        // Para true, debe de haber una coma fuera de comillas o un ; , y no ser el fin de la linea.
        line = line.trim();
        boolean insideString = false;
        boolean shouldEnd = false;
        char previousChar = 0;

        for (char currentChar : line.toCharArray()) {
            //Si entra aqui, es que hay texto despues de la linea, y deberia de acabar.
            if (shouldEnd) {
                return true;
            }


            // Comprobar si estamos dentro de una cadena
            if (currentChar == '"' && previousChar != '\\') {
                insideString = !insideString;
            }


            // Si encontramos una coma fuera de una cadena, devolvemos true
            if ((currentChar == ',' && !insideString) || (currentChar == ';' && !insideString)) {
                shouldEnd = true;
            }

            previousChar = currentChar;
        }

        return false;
    }

    private static void gestionarMutlipleEnums(String line, StringBuilder sb, Context context) {
        //        ES_HOLA_("ES, \"(hola)\""), EN("EN"), JP("JP");

        // Para true, debe de haber una coma fuera de comillas o un ; , y no ser el fin de la linea.
        line = line.trim();
        boolean insideString = false;
        boolean shouldEnd = false;
        char previousChar = 0;
        StringBuilder part = new StringBuilder();

        for (char currentChar : line.toCharArray()) {
            //Si entra aqui, es que hay texto despues de la linea, y deberia de acabar.
            if (shouldEnd) {
                sb.append(TAB.repeat(context.depth)).append(part.toString().trim()).append(System.lineSeparator());
                part.setLength(0);
                shouldEnd = false;
            }


            // Comprobar si estamos dentro de una cadena
            if (currentChar == '"' && previousChar != '\\') {
                insideString = !insideString;
            }


            // Si encontramos una coma fuera de una cadena, devolvemos true
            if ((currentChar == ',' && !insideString) || (currentChar == ';' && !insideString)) {
                shouldEnd = true;
            }

            previousChar = currentChar;
            part.append(currentChar);
        }

        if (shouldEnd) {
            sb.append(TAB.repeat(context.depth)).append(part.toString().trim()).append(System.lineSeparator());
        }
    }

    private static void processAnnotationsLine(String line, StringBuilder sb, Context context) {
        line = line.trim();
        String notacion = getNotation(line);

        if (!context.lastLineNotation && !context.lastLineEmpty) {
            sb.append(System.lineSeparator());
        }

        // quitar paquete de la anotación
        if (notacion.contains(".")) {
            int p = notacion.indexOf("(") > 0 ? notacion.indexOf("(") : notacion.length();
            String base = notacion.substring(1, p); // sin @
            String params = notacion.substring(p);
            base = base.substring(base.lastIndexOf(".") + 1);
            notacion = "@" + base + params;
        }

//        if (!context.lastLineNotation && !context.lastLineEmpty && !sb.isEmpty()) sb.append(System.lineSeparator());

        sb.append(TAB.repeat(context.depth)).append(notacion).append(System.lineSeparator());

        // Obtener lo que queda DESPUÉS de la anotación sin usar replace()
        String resto = line.substring(getNotationEndIndex(line)).trim();

        context.lastLineNotation = true;

        //Volver a procesar el resto de la línea si queda algo
        if (!resto.isEmpty()) {
            processLine(resto, sb, context);
        }

    }


    @NotNull
    private static String getNotation(String line) {
        int end = getNotationEndIndex(line);
        return line.substring(0, end);
    }

    private static int getNotationEndIndex(String line) {
        if (line.contains("(")) {
            int c = line.indexOf(")");
            return c > 0 ? c + 1 : line.length();
        }
        int sp = line.indexOf(" ");
        return sp > 0 ? sp : line.length();
    }

    private static void processNormalLine(String line, StringBuilder sb, Context context) {
        if (line.isEmpty()) throw new IllegalArgumentException("Invalid annotation line: " + line);
        if (line.startsWith("@")) throw new IllegalArgumentException("Invalid annotation line: " + line);


        // Si venimos de anotaciones → mete salto ANTES del campo
        sb.append(TAB.repeat(context.depth)).append(line).append(System.lineSeparator());

        context.lastLineNotation = false;
        context.lastLineEmpty = false;
        context.insideEnum = line.contains("public enum");
        if (context.insideEnum) {
            sb.append(System.lineSeparator());
        }
    }

    private static class Context {
        boolean lastLineEmpty = false;
        boolean lastLineNotation = false;
        boolean insideEnum = false;
        int depth = 0;
    }

}
