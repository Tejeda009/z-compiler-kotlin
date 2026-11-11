#include <stdint.h>
#include <stdio.h>

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    int64_t state = 12345 + seed;
    const int64_t modulus = 2147483648;
    const int64_t samples = 50000000;
    int64_t hits = 0;

    for (int64_t i = 0; i < samples; i = i + 1) {
        state = (state * 1103515245 + 12345) % modulus;
        const double x = (double)state * 1.0 / 2147483648.0;
        state = (state * 1103515245 + 12345) % modulus;
        const double y = (double)state * 1.0 / 2147483648.0;

        if (x * x + y * y < 1.0) {
            hits = hits + 1;
        }
    }

    printf("%lld\n", (long long)hits);
    return 0;
}
