#include <stdint.h>
typedef void (*passki_callback)(int32_t status, const char *message, void *ctx);
void passki_report_unknown(const char *rp_id, const uint8_t *credential_id, int32_t credential_id_length,
                           passki_callback callback, void *ctx);
void passki_report_all_accepted(const char *rp_id, const uint8_t *user_handle, int32_t user_handle_length,
                                const uint8_t *const *ids, const int32_t *id_lengths, int32_t count,
                                passki_callback callback, void *ctx);
void passki_report_details(const char *rp_id, const uint8_t *user_handle, int32_t user_handle_length,
                           const char *new_name, passki_callback callback, void *ctx);
