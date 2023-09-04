package info.kgeorgiy.ja.zakharov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static info.kgeorgiy.ja.zakharov.implementor.ImplementorUtilities.TAB;
import static info.kgeorgiy.ja.zakharov.implementor.ImplementorUtilities.newLine;

/**
 * The Implementor class is used to create implementation of classes
 * using method {@link Implementor#implement(Class, Path)} creates .java file that implements or extends {@code Class}
 * using method {@link Implementor#implementJar(Class, Path)} creates .jar file that implements or extends {@code Class}
 */
public class Implementor implements JarImpler {
    /**
     * {@link Manifest} for jar file implementation.
     */
    private static final Manifest MANIFEST = makeManifest();

    /**
     * Creates {@link Manifest} for jar file with simple Attributes.
     *
     * @return {@link Manifest} for jar file.
     */
    private static Manifest makeManifest() {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_VENDOR, "Kirill Zakharov");
        return manifest;
    }

    /**
     * Creates new instance of {@link Implementor}.
     */
    public Implementor() {
    }

    /**
     * Compares return values from methods using isAssignableFrom. Method with narrow return type will be return.
     *
     * @param method1 the reference to {@link Method}
     * @param method2 the reference to {@link Method}
     * @return method with narow return type
     */

    private Method getNarrow(Method method1, Method method2) {
        if (method1 == null) {
            return method2;
        }
        if (method2.getReturnType().isAssignableFrom(method1.getReturnType())) {
            return method1;
        }
        return method2;
    }

    /**
     * Gets methods from class and returns {@link Map} of methods that gets from getter and satisfies predicate, if
     * class has multiple methods with the same signature in map will be lie method with narrow return type.
     *
     * @param token     class to get methods
     * @param predicate {@link Predicate} to filter methods
     * @param getter    {@link Function} to get methods
     * @return {@link Map} from {@link MethodWrapper} to {@link Method} of methods from getter satisfies a predicate
     */
    private Map<MethodWrapper, Method> getMethodsMap(Class<?> token, Predicate<? super Method> predicate,
                                                     Function<Class<?>, Method[]> getter) {
        return Arrays.stream(getter.apply(token))
                .filter(predicate)
                .collect(Collectors.toMap(MethodWrapper::new, Function.identity(), this::getNarrow));
    }

    /**
     * Get all methods in {@code token} and its superclasses that satisfy a {@code predicate}, if class has several
     * methods with same signature and different return types will be chosen with narrow return type.
     *
     * @param token     class to get methods
     * @param predicate {@link Predicate} to filter methods in {}
     * @return {@link Set} of {@link MethodWrapper}
     * that contains all methods in {@code token}, that satisfy {@code predicate}
     */
    private Set<MethodWrapper> getMethodsPredicate(Class<?> token, Predicate<? super Method> predicate) {
        Map<MethodWrapper, Method> storage = getMethodsMap(token, predicate, Class::getMethods);
        while (token != null) {
            Map<MethodWrapper, Method> tmp = getMethodsMap(token, predicate, Class::getDeclaredMethods);
            tmp.forEach(((methodWrapper, method) -> storage.merge(methodWrapper, method, this::getNarrow)));
            token = token.getSuperclass();
        }
        return storage.values().stream().map(MethodWrapper::new).collect(Collectors.toSet());
    }

    /**
     * Get all methods in {@code token} that have abstract modifier in super of {@code token} entity and doesn't have
     * final modifier in any super of {@code token}.
     *
     * @param token class to get methods
     * @return {@link List} of {@link Method} in {@code token}
     * that need to be overridden for correct definition of {@code token} entity
     */
    private List<Method> getMethods(Class<?> token) {
        Set<MethodWrapper> storage = getMethodsPredicate(token, method -> Modifier.isAbstract(method.getModifiers()));
        storage.removeAll(getMethodsPredicate(token, method -> Modifier.isFinal(method.getModifiers())));
        return storage.stream().map(MethodWrapper::method).toList();
    }

    /**
     * Get all constructors that declared in {@code token} and don't have private modifier.
     *
     * @param token class to get constructors
     * @return {@link Set} of {@link Constructor} that need override for correct definition of {@code token} entity
     */
    private List<Constructor<?>> getConstructors(Class<?> token) {
        return Arrays.stream(token.getDeclaredConstructors())
                .filter(func -> !Modifier.isPrivate(func.getModifiers()))
                .toList();
    }

    /**
     * Return path to file, containing implementation of given class or interface.
     *
     * @param path  {@link Path} to parent directory of class
     * @param token class or interface to get name
     * @param end   end file extension
     * @return {@link Path} to new file for implementing
     */
    private Path getFilePath(Path path, Class<?> token, String end) {
        return path.resolve(token.getPackageName().replace('.', File.separatorChar))
                .resolve(getClassName(token) + end);
    }

    /**
     * Returns of concatenation with canonical name and parameter name.
     *
     * @param parameter {@link Parameter}
     * @return {@link String} representing value
     */
    private String getFullName(Parameter parameter) {
        return parameter.getType().getCanonicalName() + " " + parameter.getName();
    }

    /**
     * Gets default value of given class.
     *
     * @param token class to get default value
     * @return {@link String} representing value
     */
    private String getDefaultValue(Class<?> token) throws ImplerException {
        if (token.equals(boolean.class)) {
            return " false";
        } else if (token.equals(void.class)) {
            return "";
        } else if (token.isPrimitive()) {
            return " 0";
        }
        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Return value shouldn't be null");
        }
        return " null";
    }

    /**
     * Returns {@link String} that contains body for function with return type {@code method} and returns
     * {@link #getDefaultValue}.
     *
     * @param method {@link Method}
     * @return {@link String} body for default implementation of {@code method}
     */
    private String getBody(Method method) throws ImplerException {
        return "{" + newLine(2) +
                "return" + getDefaultValue(method.getReturnType()) + ";" +
                newLine(1) + "}" + newLine() + newLine();
    }

    /**
     * Returns {@link StringBuilder} that contains {@code executable} with public modifier.
     *
     * @param executable {@link Executable} for getting modifiers
     * @return {@link StringBuilder} representation value
     */
    private StringBuilder getBeginOfExecutableDeclaration(Executable executable) {
        return new StringBuilder(TAB).append("public");
    }

    /**
     * Append to {@code sb} parameters which takes an {@code executable} and exceptions that {@code executable} will be thrown.
     *
     * @param executable {@link Executable} from whom will be taken parameters
     * @param sb         {@link StringBuilder} to which will be added end of executable definition
     */
    private void getEndOfExecutableDeclaration(Executable executable, StringBuilder sb) throws ImplerException {
        sb.append("(");
        if (Arrays.stream(executable.getParameters()).anyMatch(parameter -> Modifier.isPrivate(parameter.getType().getModifiers()))) {
            throw new ImplerException("Parameters should be not private classes");
        }
        sb.append(Arrays.stream(executable.getParameters()).map(this::getFullName)
                .collect(Collectors.joining(", ")));
        sb.append(")").append(" ");
        Class<?>[] exceptions = executable.getExceptionTypes();
        if (exceptions.length > 0) {
            sb.append("throws ").append(Arrays.stream(exceptions)
                    .map(Class::getCanonicalName).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Writes default definition of {@code method} by {@code writer}.
     *
     * @param method {@link Method} that need to write
     * @param writer {@link Writer} that will be used for writing
     * @throws IOException that will be thrown by writer
     */
    private void writeMethod(Method method, Writer writer) throws IOException, ImplerException {
        StringBuilder output = getBeginOfExecutableDeclaration(method);
        output.append(" ").append(method.getReturnType().getCanonicalName()).append(" ").append(method.getName());
        getEndOfExecutableDeclaration(method, output);
        output.append(getBody(method));
        writer.write(output.toString());
    }

    /**
     * Writes definition of constructor that call super with given arguments in {@code constructor}.
     *
     * @param token       class which constructor need to write
     * @param constructor {@link Constructor} that need to call in body of constructor
     * @param writer      {@link Writer} writes definition
     * @throws IOException if {@code writer} I/O will be occurred
     */
    private void writeConstructor(Class<?> token, Constructor<?> constructor, Writer writer) throws IOException, ImplerException {
        StringBuilder output = getBeginOfExecutableDeclaration(constructor);
        output.append(" ").append(getClassName(token));
        getEndOfExecutableDeclaration(constructor, output);
        output.append("{").append(newLine(2)).append("super(");
        Parameter[] parameters = constructor.getParameters();
        output.append(Arrays.stream(parameters).map(Parameter::getName).collect(Collectors.joining(", ")));
        output.append(");").append(newLine(1));
        output.append("}").append(newLine()).append(newLine());
        writer.write(output.toString());
    }

    /**
     * Write default definition for {@code token} entity using {@link #writeMethod(Method, Writer)} for every method
     * that need to be overridden.
     *
     * @param token  class which methods need to write
     * @param writer {@link Writer} writes definition
     * @throws IOException if {@code writer} I/O will be occurred
     */
    private void writeMethods(Class<?> token, Writer writer) throws IOException, ImplerException {
        List<Method> methods = getMethods(token);
        for (Method method : methods) {
            writeMethod(method, writer);
        }
    }


    /**
     * Gets simpleName and adds "Impl" suffix.
     *
     * @param token class or interface which name need to get
     * @return {@link String} representation {@code token} entity implementation
     */
    private String getClassName(Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    /**
     * Writes possible implementation of all not private constructors declared in class.
     *
     * @param token  class which constructors need to write
     * @param writer {@link Writer} writes definition
     * @throws IOException     if I\O was occurred
     * @throws ImplerException if class not interface and contains only private constructors
     */
    private void writeConstructors(Class<?> token, Writer writer) throws IOException, ImplerException {
        if (!token.isInterface()) {
            List<Constructor<?>> constructors = getConstructors(token);
            if (constructors.size() == 0) {
                throw new ImplerException("Number of constructors must be not zero");
            }
            for (Constructor<?> constructor : constructors) {
                writeConstructor(token, constructor, writer);
            }
        }
    }

    /**
     * Create directories for {@code path}.
     *
     * @param path {@link Path} directories that need to create
     */
    private void createDirectories(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignored) {
        }
    }

    /**
     * Construct {@link String} package of given class.
     *
     * @param token class or interface for getting package
     * @return {@link String} representing package
     */
    private String getPackage(Class<?> token) {
        StringBuilder sb = new StringBuilder();
        if (!token.getPackage().getName().equals("")) {
            sb.append("package ").append(token.getPackageName()).append(";").append(newLine()).append(newLine());
        }
        return sb.toString();
    }

    /**
     * Write first {@link String} begin of class definition: package, name, implements or extends, superClass.
     *
     * @param token  class or interface - super of class
     * @param writer {@link Writer} writes definition
     * @throws IOException if I\O was occurred
     */
    private void writeClassHeader(Class<?> token, Writer writer) throws IOException {
        StringBuilder sb = new StringBuilder(getPackage(token));
        sb.append("public class ").append(getClassName(token)).append(" ");
        if (token.isInterface()) {
            sb.append("implements ");
        } else {
            sb.append("extends ");
        }
        sb.append(token.getCanonicalName());
        sb.append(" {").append(newLine());
        writer.write(sb.toString());
    }


    /**
     * Creates a possible implementation of class in {@code token} entity.
     * Generated class full name same as full name of the type token with "Impl" suffix added.
     *
     * @param token type token to create implementation for
     * @param root  root directory
     * @throws ImplerException if the given class cannot be generated for one of such reasons:
     *                         <ul>
     *                         <li> Some arguments are {@code null}</li>
     *                         <li> Given {@code tokem} is Enum, Array, Primitive, Final class or Private class </li>
     *                         <li> Given class isn't interface and contains only private constructors </li>
     *                         <li> The problems with I/O occurred during implementation. </li>
     *                         </ul>
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        Objects.requireNonNull(token);
        Objects.requireNonNull(root);
        if (token.isArray() || token.isPrimitive() || token == Enum.class
                || Modifier.isFinal(token.getModifiers()) || Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Token must be interface or class");
        }
        Path rootPath = getFilePath(root, token, ".java");
        createDirectories(rootPath);
        try (BufferedWriter writer = Files.newBufferedWriter(rootPath)) {
            try {
                writeClassHeader(token, writer);
                writeConstructors(token, writer);
                writeMethods(token, writer);
                writer.write("}" + newLine());
            } catch (IOException e) {
                throw new ImplerException("Exception in writing java file: " + e);
            }
        } catch (IOException | InvalidPathException e) {
            throw new ImplerException("Cannot create file: " + e);
        }
    }

    /**
     * Cleans all files and directory in directory {@code root}.
     *
     * @param root start directory
     * @throws IOException if I/O was occurred
     */
    private static void clean(final Path root) throws IOException {
        if (root == null) {
            return;
        }
        if (Files.exists(root)) {
            Files.walkFileTree(root, ImplementorUtilities.DELETE_VISITOR);
        }
    }

    /**
     * Get class dependencies and returns class-path for them.
     *
     * @param token for which dependencies need to find
     * @return {@link String} representation of value.
     * @throws ImplerException if classes wasn't find
     */
    private String getClassPath(Class<?> token) throws ImplerException {
        try {
            return Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new ImplerException("Unable to find classes: " + e);
        }
    }

    /**
     * Creates {@code .jar} file implementing class or interface {@code token} entity.
     * Generated class full name should be same as full name of the type token with "Impl" suffix
     * added.
     * During implementation creates temporary folder to store temporary .java and .class files.
     * If program fails to delete temporary folder, it informs user about it.
     *
     * @throws ImplerException if the given class cannot be generated for one of such reasons:
     *                         <ul>
     *                         <li> Some arguments are {@code null}</li>
     *                         <li> Error occurs during implementation via {@link #implement(Class, Path)} </li>
     *                         <li> {@link JavaCompiler} failed to compile implemented class </li>
     *                         <li> The problems with I/O occurred during implementation. </li>
     *                         </ul>
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Objects.requireNonNull(token);
        Objects.requireNonNull(jarFile);
        createDirectories(jarFile);
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(jarFile.toAbsolutePath().getParent(), "temp");
            implement(token, tmpDir);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new ImplerException("Unable to compile generated files");
            }
            String classpath = jarFile + File.pathSeparator + getClassPath(token);
            String[] args = {getFilePath(tmpDir, token, ".java").toString(), "-encoding", "UTF-8", "-cp", classpath};
            if (compiler.run(null, null, null, args) != 0) {
                throw new ImplerException("Cannot compile files");
            }
            try (JarOutputStream writer = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(
                    jarFile)), MANIFEST)) {
                JarEntry jarAdd = new JarEntry((token.getPackageName() + "." + token.getSimpleName())
                        .replace('.', '/') + "Impl.class");
                writer.putNextEntry(jarAdd);
                Files.copy(getFilePath(tmpDir, token, ".class"), writer);
            } catch (IOException e) {
                throw new ImplerException("Cannot write to JAR file: " + e);
            }
        } catch (IOException e) {
            throw new ImplerException("Unable to create files: " + e);
        } finally {
            try {
                clean(tmpDir);
            } catch (IOException e) {
                System.err.println("Cannot clear tmp directory" + e);
            }
        }

    }

    /**
     * This function decides which file to generate (.java or .jar) realization for class in {@code args} depending on arguments.
     *
     * @param args array of {@link String} with length 2 or 3. If length of args == 2, expected format:
     *             class-name path, where class-name - name for class implementation, path - path to implementation place;
     *             If length of args == 3, expected format: -jar class-name path, class-name - name for class implementation,
     *             path - path to implementation place of jar file
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 2 && args.length != 3)) {
            System.err.println("Invalid number of arguments");
            return;
        }
        for (String arg : args) {
            if (arg == null) {
                System.err.println("Argument shouldn't be null");
                return;
            }
        }
        Implementor implementor = new Implementor();
        try {
            if (args.length == 2) {
                implementor.implement(Class.forName(args[0]), Path.of(args[1]));
            } else if (args[0].equals("-jar")) {
                implementor.implementJar(Class.forName(args[1]), Path.of(args[2]));
            }
        } catch (ImplerException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
