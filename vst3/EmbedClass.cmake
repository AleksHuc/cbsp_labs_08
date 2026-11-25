file(READ "${CLASS_FILE}" bytecode HEX)
string(REGEX REPLACE "(..)" "0x\\1," bytecode "${bytecode}")
file(WRITE "${OUTPUT_FILE}" "static constexpr unsigned char vst3DspClass[] = {${bytecode}};\n")
