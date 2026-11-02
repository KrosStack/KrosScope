cmake_minimum_required(VERSION 3.22)
project(tasktide_checksum CXX)

find_package(JNI REQUIRED)

add_library(tasktide_checksum SHARED src/checksum.cpp)
target_include_directories(tasktide_checksum PRIVATE ${JNI_INCLUDE_DIRS})
