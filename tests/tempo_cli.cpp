#include "tempo.h"
#include <cstdio>
#include <cstdlib>
#include <exception>
#include <stdexcept>

int main(int argc, char **argv) {
    if (argc != 4 && argc != 5) return 2;
    try {
        int64_t last = -1, target = 0;
        const auto frames = changeTempo(argv[1], argv[2], atoi(argv[3]), [&](int64_t completed, int64_t total) {
            if (completed <= last || total <= 0 || completed > total || (last < 0 && completed != 0))
                throw std::runtime_error("Invalid progress sequence");
            last = completed;
            target = total;
            if (argc == 5 && completed >= atoll(argv[4]))
                throw std::runtime_error("Cancelled by test");
        });
        if (last != frames || target != frames) throw std::runtime_error("Missing final progress");
        printf("%lld\n", static_cast<long long>(frames));
        return 0;
    } catch (const std::exception &e) {
        fprintf(stderr, "%s\n", e.what());
        return 1;
    }
}
