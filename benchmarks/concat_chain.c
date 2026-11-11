#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *concat(const char *x, const char *y) {
    const size_t xl = strlen(x);
    const size_t yl = strlen(y);
    char *r = malloc(xl + yl + 1);
    if (r == NULL) exit(1);
    memcpy(r, x, xl);
    memcpy(r + xl, y, yl + 1);
    return r;
}

int main(void) {
    const char *a = "alpha-0123456789";
    const char *b = "beta-ABCDEFGHIJ";
    const char *c = "gamma-klmnopqrstuvwxyz";
    const int64_t iterations = 5000000;
    int64_t checksum = 0;

    for (int64_t i = 0; i < iterations; i++) {
        char *ab = concat(a, b);
        char *abc = concat(ab, c);
        free(ab);
        checksum += (int64_t)strlen(abc);
        free(abc);
    }

    printf("%lld\n", (long long)checksum);
    return 0;
}
