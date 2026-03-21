cmake_minimum_required(VERSION 3.22.1)

project("kail_native_hook")

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(dobby REQUIRED CONFIG)

add_library(kail_native_hook SHARED
        main.cpp
        elf_util.cpp
        sensor_hook.cpp)

target_link_libraries(kail_native_hook android log)
target_link_libraries(kail_native_hook dobby::dobby)
