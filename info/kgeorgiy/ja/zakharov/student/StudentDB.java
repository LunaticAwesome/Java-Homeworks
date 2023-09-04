package info.kgeorgiy.ja.zakharov.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements AdvancedQuery {
    private static final String EMPTY_STRING = "";
    private static final Comparator<String> defaultStringComparator = Comparator.reverseOrder();
    private static final Comparator<Student> defaultComparator =
            Comparator.comparing(Student::getLastName, defaultStringComparator)
                    .thenComparing(Student::getFirstName, defaultStringComparator)
                    .thenComparing(Student::getId).thenComparing(Student::getGroup);
    private static final Comparator<Student> idComparator = Comparator.comparing(Student::getId);


    private <T, C extends Collection<T>>
    C mappedStudentsCollection(List<Student> students, Function<Student, T> mapper, Supplier<C> collectionFactory) {
        return students.stream().map(mapper).collect(Collectors.toCollection(collectionFactory));
    }

    private <T> List<T> mappedStudentsList(List<Student> students, Function<Student, T> mapper) {
        return mappedStudentsCollection(students, mapper, ArrayList::new);
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return mappedStudentsList(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return mappedStudentsList(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return mappedStudentsList(students, Student::getGroup);
    }

    private String getFullName(Student student) {
        return student.getFirstName() + " " + student.getLastName();
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return mappedStudentsList(students, this::getFullName);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return mappedStudentsCollection(students, Student::getFirstName, TreeSet::new);
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream().max(idComparator)
                .map(Student::getFirstName).orElse(EMPTY_STRING);
    }

    private List<Student> sortStudents(Collection<Student> students, Comparator<Student> studentComparator) {
        return students.stream().sorted(studentComparator).collect(Collectors.toList());
    }

    private Stream<Student> filterStudentsAndSort(Collection<Student> students, Predicate<Student> predicate,
                                                  Comparator<Student> comparator) {
        return students.stream().filter(predicate).sorted(comparator);
    }

    private List<Student> findStudentsByPredicate(Collection<Student> students, Predicate<Student> predicate) {
        return filterStudentsAndSort(students, predicate, defaultComparator).collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStudents(students, idComparator);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudents(students, defaultComparator);
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findStudentsByPredicate(students, student -> student.getFirstName().equals(name));
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findStudentsByPredicate(students, student -> student.getLastName().equals(name));
    }

    private Predicate<Student> groupNamePredicate(GroupName group) {
        return student -> student.getGroup().equals(group);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findStudentsByPredicate(students, groupNamePredicate(group));
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return filterStudentsAndSort(students, groupNamePredicate(group), defaultComparator)
                .collect(Collectors.toMap(Student::getLastName,
                        Student::getFirstName, BinaryOperator.minBy(String::compareTo)));
    }

    private <C> Stream<Map.Entry<C, List<Student>>>
    getEntryStream(Collection<Student> students, Function<Student, C> classifier,
                   Supplier<Map<C, List<Student>>> mapFactory) {
        return students.stream().collect(Collectors
                .groupingBy(classifier, mapFactory, Collectors.toList())).entrySet().stream();
    }

    private List<Group> getSortedGroups(Collection<Student> students, UnaryOperator<List<Student>> sorter) {
        return getEntryStream(students, Student::getGroup, TreeMap::new)
                .map(entry -> new Group(entry.getKey(), sorter.apply(entry.getValue())))
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getSortedGroups(students, this::sortStudentsByName);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getSortedGroups(students, this::sortStudentsById);
    }

    private GroupName getLargestGroupComparator(Collection<Student> students, ToIntFunction<List<Student>> valueFunc,
                                                Comparator<GroupName> thenComparator) {
        return getEntryStream(students, Student::getGroup, HashMap::new).max(Comparator
                .comparingInt((Map.Entry<GroupName, List<Student>> entry) -> valueFunc.applyAsInt(entry.getValue()))
                .thenComparing(Map.Entry::getKey, thenComparator)).map(Map.Entry::getKey).orElse(null);
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargestGroupComparator(students, List::size, Comparator.naturalOrder());
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargestGroupComparator(students, studentsList -> getDistinctFirstNames(studentsList).size(),
                Comparator.reverseOrder());
    }

    private long distinctGroupNumbers(List<Student> students) {
        return students.stream().map(Student::getGroup).distinct().count();
    }

    @Override
    public String getMostPopularName(Collection<Student> students) {
        return getEntryStream(students, Student::getFirstName, HashMap::new).max(Comparator
                        .comparingLong((Map.Entry<String, List<Student>> entry) -> distinctGroupNumbers(entry.getValue()))
                        .thenComparing(Map.Entry::getKey, defaultStringComparator))
                .map(Map.Entry::getKey).orElse(EMPTY_STRING);
    }

    private <R> List<R> getStudentsIds(Collection<Student> students, int[] ids,
                                       Function<Student, R> mapper) {
        return Arrays.stream(ids)
                .mapToObj(students.stream().collect(Collectors.toMap(Student::getId, UnaryOperator.identity()))::get)
                .map(mapper).toList();
    }

    @Override
    public List<String> getFirstNames(Collection<Student> students, int[] ids) {
        return getStudentsIds(students, ids, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(Collection<Student> students, int[] ids) {
        return getStudentsIds(students, ids, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> students, int[] ids) {
        return getStudentsIds(students, ids, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(Collection<Student> students, int[] ids) {
        return getStudentsIds(students, ids, this::getFullName);
    }
}