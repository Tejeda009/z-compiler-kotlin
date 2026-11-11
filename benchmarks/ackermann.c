#include <stdint.h>
#include <stdio.h>

static int64_t ackermann(int64_t m, int64_t n) {
    if (m == 0) return n + 1;
    if (n == 0) return ackermann(m - 1, 1);
    return ackermann(m - 1, ackermann(m, n - 1));
}

int main(int argc, char **argv) {
    (void)argv;
    const int64_t rounds = 100;
    const int64_t seed = argc;
    int64_t total = 0;

    for (int64_t i = 0; i < rounds; i++) {
        total += ackermann(3, 4 + ((seed + i) % 5));
    }

    printf("%lld\n", (long long)total);
    return 0;
}
