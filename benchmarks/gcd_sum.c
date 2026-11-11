#include <stdint.h>
#include <stdio.h>

static int64_t gcd(int64_t a, int64_t b) {
    int64_t x = a;
    int64_t y = b;

    for (int64_t guard = 0; y > 0; guard = guard + 1) {
        const int64_t r = x % y;
        x = y;
        y = r;
    }

    return x;
}

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    const int64_t n = 3500;
    int64_t total = 0;

    for (int64_t i = 1; i < n + 1; i = i + 1) {
        for (int64_t j = 1; j < n + 1; j = j + 1) {
            total = total + gcd(i + seed - 1, j);
        }
    }

    printf("%lld\n", (long long)total);
    return 0;
}
