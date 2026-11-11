#include <stdint.h>
#include <stdio.h>

static void accumulate(int64_t *acc, int64_t value) {
    *acc = *acc + (value * value) % 7;
}

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    int64_t total = 0;

    for (int64_t i = 0; i < 300000000; i = i + 1) {
        accumulate(&total, i + seed);
    }

    printf("%lld\n", (long long)total);
    return 0;
}
