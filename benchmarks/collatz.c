#include <stdint.h>
#include <stdio.h>

static int64_t collatz_steps(int64_t start) {
    int64_t n = start;
    int64_t steps = 0;

    for (int64_t guard = 0; n > 1; guard = guard + 1) {
        const int64_t odd = n % 2;
        if (odd == 0) {
            n = n / 2;
        }
        if (odd == 1) {
            n = 3 * n + 1;
        }
        steps = steps + 1;
    }

    return steps;
}

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    const int64_t limit = 1000000;
    int64_t best_start = 0;
    int64_t best_steps = 0;
    int64_t total = 0;

    for (int64_t i = 1; i < limit; i = i + 1) {
        const int64_t s = collatz_steps(i + seed - 1);
        if (s > best_steps) {
            best_steps = s;
            best_start = i;
        }
        total = total + s;
    }

    printf("%lld %lld %lld\n", (long long)best_start, (long long)best_steps, (long long)total);
    return 0;
}
