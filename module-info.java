module info.kgeorgiy.ja.zakharov {
    requires info.kgeorgiy.java.advanced.implementor;
    requires java.compiler;
    requires info.kgeorgiy.java.advanced.student;
    requires info.kgeorgiy.java.advanced.concurrent;
    requires info.kgeorgiy.java.advanced.mapper;
    requires info.kgeorgiy.java.advanced.crawler;
    requires info.kgeorgiy.java.advanced.hello;

    opens info.kgeorgiy.ja.zakharov.student;
    opens info.kgeorgiy.ja.zakharov.implementor;
    opens info.kgeorgiy.ja.zakharov.arrayset;
    opens info.kgeorgiy.ja.zakharov.walk;
    opens info.kgeorgiy.ja.zakharov.concurrent;
    opens info.kgeorgiy.ja.zakharov.crawler;
    opens info.kgeorgiy.ja.zakharov.hello;
}
