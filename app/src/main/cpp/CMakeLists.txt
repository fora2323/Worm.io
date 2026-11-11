cmake_minimum_required(VERSION 3.22.1)
project("worm_engine")

add_library(worm_engine SHARED
    worm_engine.cpp
)

find_library(log-lib log)

target_link_libraries(worm_engine
    ${log-lib}
)