
#include "common.h"

namespace limax {

	MarshalStreamSource::MarshalStreamSource() {}
	MarshalStreamSource::~MarshalStreamSource() {}

	UnmarshalStreamSource::UnmarshalStreamSource() {}
	UnmarshalStreamSource::~UnmarshalStreamSource() {}

} // namespace limax {

namespace limax {

	MarshalStream::MarshalStream(std::shared_ptr<MarshalStreamSource> src)
		: source(src)
	{}

	MarshalStream::MarshalStream(MarshalStreamSource& src)
		: source(std::shared_ptr<MarshalStreamSource>(&src, [](MarshalStreamSource*){}))
	{}

	MarshalStream::MarshalStream(Octets& src)
		: source(std::make_shared<OctetsMarshalStreamSource>(src))
	{}

	MarshalStream::~MarshalStream() {}

	MarshalStream& MarshalStream::push(bool x)
	{
		return push_byte((int8_t)(x ? 1 : 0));
	}

	MarshalStream& MarshalStream::push(int8_t x)
	{
		return push_byte(x);
	}
	MarshalStream& MarshalStream::push(uint8_t x)
	{
		return push_byte(x);
	}
	MarshalStream& MarshalStream::push(int16_t x)
	{
		return push_byte(htobe16(x));
	}
	MarshalStream& MarshalStream::push(uint16_t x)
	{
		return push_byte(htobe16(x));
	}
	MarshalStream& MarshalStream::push(int32_t x)
	{
		return push_byte(htobe32(x));
	}
	MarshalStream& MarshalStream::push(uint32_t x)
	{
		return push_byte(htobe32(x));
	}
	MarshalStream& MarshalStream::push(int64_t x)
	{
		return push_byte(htobe64(x));
	}
	MarshalStream& MarshalStream::push(uint64_t x)
	{
		return push_byte(htobe64(x));
	}

	MarshalStream& MarshalStream::push(float x)
	{
		uint32_t y = aliasing_cast<uint32_t>(x);
		return push(y);
	}
	MarshalStream& MarshalStream::push(double x)
	{
		uint64_t y = aliasing_cast<uint64_t>(x);
		return push(y);
	}

	MarshalStream& MarshalStream::push(const Marshal& x)
	{
		return x.marshal(*this);
	}
	MarshalStream& MarshalStream::push(const Octets& x)
	{
		uint32_t size = (uint32_t)x.size();
		marshal_size(size);
		source->pushbytes(x.begin(), x.size());
		return *this;
	}

	MarshalStream& MarshalStream::marshal_size(int32_t x)
	{
		if (x >= 0)
		{
			if (x < 0x80)
				return push((uint8_t)x);
			if (x < 0x4000)
				return push((uint8_t)((x >> 8) | 0x80)).push((uint8_t)x);
			if (x < 0x200000)
				return push((uint8_t)((x >> 16) | 0xc0)).push((uint8_t)(x >> 8)).push((uint8_t)x);
			if (x < 0x10000000)
				return push((uint8_t)((x >> 24) | 0xe0)).push((uint8_t)(x >> 16)).push((uint8_t)(x >> 8)).push((uint8_t)x);
		}
		return push((uint8_t)0xf0).push(x);
	}
} // namespace limax {

namespace limax {

	UnmarshalStream::UnmarshalStream(std::shared_ptr<UnmarshalStreamSource> src)
		: source(src)
	{}
	UnmarshalStream::UnmarshalStream(UnmarshalStreamSource& src)
		: source(std::shared_ptr<UnmarshalStreamSource>(&src, [](UnmarshalStreamSource*){}))
	{}

	UnmarshalStream::UnmarshalStream(Octets& src)
		: source(std::shared_ptr<UnmarshalStreamSource>(new OctetsUnmarshalStreamSource(src)))
	{}

	UnmarshalStream::~UnmarshalStream() {}

	uint8_t UnmarshalStream::pop_byte_8() const
	{
		uint8_t c;
		pop_byte(c);
		return c;
	}
	uint16_t UnmarshalStream::pop_byte_16() const
	{
		uint16_t s;
		pop_byte(s);
		return be16toh(s);
	}
	uint32_t UnmarshalStream::pop_byte_32() const
	{
		uint32_t l;
		pop_byte(l);
		return be32toh(l);
	}
	uint64_t UnmarshalStream::pop_byte_64() const
	{
		uint64_t ll;
		pop_byte(ll);
		return be64toh(ll);
	}

	bool UnmarshalStream::unmarshal_bool() const
	{
		return !!pop_byte_8();
	}
	int8_t UnmarshalStream::unmarshal_byte() const
	{
		return (int8_t)pop_byte_8();
	}
	uint8_t UnmarshalStream::pop_uint_8() const
	{
		return pop_byte_8();
	}
	int16_t UnmarshalStream::unmarshal_short() const
	{
		return (int16_t)pop_byte_16();
	}
	uint16_t UnmarshalStream::pop_uint_16() const
	{
		return pop_byte_16();
	}
	int32_t UnmarshalStream::unmarshal_int() const
	{
		return (int32_t)pop_byte_32();
	}
	uint32_t UnmarshalStream::pop_uint_32() const
	{
		return pop_byte_32();
	}
	int64_t UnmarshalStream::unmarshal_long() const
	{
		return (int64_t)pop_byte_64();
	}
	uint64_t UnmarshalStream::pop_uint_64() const
	{
		return pop_byte_64();
	}
	int32_t UnmarshalStream::unmarshal_size() const
	{
		int32_t b0 = pop_byte_8();
		if ((b0 & 0x80) == 0)
			return b0;
		if ((b0 & 0x40) == 0) {
			int32_t b1 = pop_byte_8();
			return ((b0 & 0x3f) << 8) | b1;
		}
		if ((b0 & 0x20) == 0) {
			int32_t b1 = pop_byte_8();
			int32_t b2 = pop_byte_8();
			return ((b0 & 0x1f) << 16) | (b1 << 8) | b2;
		}
		if ((b0 & 0x10) == 0) {
			int32_t b1 = pop_byte_8();
			int32_t b2 = pop_byte_8();
			int32_t b3 = pop_byte_8();
			return ((b0 & 0x0f) << 24) | (b1 << 16) | (b2 << 8) | b3;
		}
		return unmarshal_int();
	}
	float UnmarshalStream::unmarshal_float() const
	{
		uint32_t l = pop_byte_32();
		return aliasing_cast<float>(l);
	}
	double UnmarshalStream::unmarshal_double() const
	{
		uint64_t l = pop_byte_64();
		return aliasing_cast<double>(l);
	}
	void UnmarshalStream::unmarshal(const Marshal& x) const
	{
		remove_const(x).unmarshal(*this);
	}
	void UnmarshalStream::unmarshal_Octets(Octets& x) const
	{
		size_t size = (size_t)unmarshal_size();
		const void* data = source->pop(size);
		x.replace(data, size);
	}
	void UnmarshalStream::pop_byte(char* x, size_t size) const
	{
		const void* data = source->pop(size);
		memcpy(x, data, size);
	}

	void UnmarshalStream::transaction(UnmarshalStreamSource::Transaction trans) const
	{
		return source->transaction(trans);
	}

} // namespace limax {

namespace limax {

	Marshal::Marshal() {}
	Marshal::~Marshal() {}

	std::ostream& Marshal::trace(std::ostream& os) const
	{
		return os;
	}

} // namespace limax {

namespace limax {

	OctetsMarshalStreamSource::OctetsMarshalStreamSource(Octets& src)
		: data(src)
	{}
	OctetsMarshalStreamSource::~OctetsMarshalStreamSource() {}

	void OctetsMarshalStreamSource::pushbytes(const void* _data, size_t _size)
	{
		data.insert(data.end(), _data, _size);
	}

} // namespace limax {

namespace limax {

	OctetsUnmarshalStreamSource::OctetsUnmarshalStreamSource(const Octets& src)
		: pos(0), tranpos(0), data(src)
	{}
	OctetsUnmarshalStreamSource::~OctetsUnmarshalStreamSource() {}

	const void* OctetsUnmarshalStreamSource::pop(size_t size) const
	{
		if ((pos + size) > data.size())
			throw MarshalException();
		const void* _data = (const char*)data.begin() + pos;
		pos += size;
		return _data;
	}

	void OctetsUnmarshalStreamSource::transaction(Transaction trans) const
	{
		switch (trans)
		{
		case Begin:
			tranpos = pos;
			break;
		case Rollback:
			pos = tranpos;
			break;
		case Commit:
			if (pos >= MAXSPARE)
			{
				Octets& data = remove_const(this->data);
				data.erase(data.begin(), (char*)data.begin() + pos);
				pos = 0;
			}
		default:
			break;
		}
	}

	bool OctetsUnmarshalStreamSource::eos() const
	{
		return data.size() == pos;
	}

	size_t OctetsUnmarshalStreamSource::position() const
	{
		return pos;
	}

	OctetsMarshalStream::OctetsMarshalStream()
		: MarshalStream(source), source(data)
	{}

	OctetsMarshalStream::~OctetsMarshalStream() {}

	Octets OctetsMarshalStream::getOctets() const
	{
		return data;
	}

} // namespace limax {

#ifdef LIMAX_OS_UNIX_FAMILY
#include <sys/stat.h>
#endif

namespace limax {
	namespace helper {

#ifdef LIMAX_OS_WINDOWS
		struct FileIO
		{
			HANDLE file;
			inline FileIO(const std::string& filename, bool readonly)
			{
				if (readonly)
					file = ::CreateFileA(filename.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
				else
					file = ::CreateFileA(filename.c_str(), GENERIC_WRITE | GENERIC_READ, 0, NULL, CREATE_ALWAYS, 0, NULL);
				if (INVALID_HANDLE_VALUE == file && Trace::isInfoEnabled())
				{
					std::ostringstream oss;
					oss << "open file readonly = " << (readonly ? "true" : "false") << " filename = \"" << filename << "\" lasterror = " << ::GetLastError();
					Trace::info(oss.str());
				}
			}
			inline ~FileIO()
			{
				if (INVALID_HANDLE_VALUE != file)
					::CloseHandle(file);
			}
			inline void write(const void* d, size_t s)
			{
				if (INVALID_HANDLE_VALUE == file)
					return;
				DWORD outsize = 0;
				if (!::WriteFile(file, d, (DWORD)s, &outsize, nullptr) && Trace::isWarnEnabled())
				{
					std::ostringstream oss;
					oss << "WriteFile size = " << s << " lasterror = " << ::GetLastError();
					Trace::warn(oss.str());
				}
			}
			inline void read(Octets& d)
			{
				if (INVALID_HANDLE_VALUE == file)
					throw MarshalException();
				DWORD size = (DWORD)d.size();
				DWORD outsize = 0;
				if (!::ReadFile(file, d.begin(), size, &outsize, nullptr))
					throw MarshalException();
				if (outsize < size)
					throw MarshalException();
			}
			inline int64_t getFileSize()
			{
				if (INVALID_HANDLE_VALUE == file)
					return 0;
				LARGE_INTEGER	filesize;
				if (::GetFileSizeEx(file, &filesize))
					return filesize.QuadPart;
				return 0;
			}
			inline int64_t getOffset()
			{
				if (INVALID_HANDLE_VALUE == file)
					return 0;
				LARGE_INTEGER	moveto;
				LARGE_INTEGER	newoffset;
				moveto.QuadPart = 0;
				if (::SetFilePointerEx(file, moveto, &newoffset, FILE_CURRENT))
					return newoffset.QuadPart;
				return 0;
			}
			inline int64_t setOffset(int64_t offset)
			{
				if (INVALID_HANDLE_VALUE == file)
					return 0;
				LARGE_INTEGER	moveto;
				LARGE_INTEGER	newoffset;
				moveto.QuadPart = offset;
				if (::SetFilePointerEx(file, moveto, &newoffset, FILE_BEGIN))
					return newoffset.QuadPart;
				return 0;
			}
			inline bool eof()
			{
				return getFileSize() == getOffset();
			}
		};
#endif // #ifdef LIMAX_OS_WINDOWS

#ifdef LIMAX_OS_UNIX_FAMILY
		struct FileIO
		{
			int file;
			inline FileIO(const std::string& filename, bool readonly)
			{
				if (readonly)
					file = open(filename.c_str(), O_RDONLY);
				else
					file = ::open(filename.c_str(), O_CREAT | O_RDWR | O_TRUNC, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
				if (-1 == file && Trace::isInfoEnabled())
				{
					std::ostringstream oss;
					oss << "open file readonly = " << (readonly ? "true" : "false") << " filename = \"" << filename << "\" lasterror = " << errno;
					Trace::info(oss.str());
				}
			}
			inline ~FileIO()
			{
				if (-1 != file)
					::close(file);
			}
			inline void write(const void* d, size_t s)
			{
				if (-1 != file)
					::write(file, d, s);
			}
			inline void read(Octets& d)
			{
				if (-1 == file)
					throw MarshalException();
				auto size = d.size();
				auto outsize = ::read(file, d.begin(), size);
				if (-1 == outsize)
					throw MarshalException();
				if ((size_t)outsize < size)
					throw MarshalException();
			}
			inline int64_t getFileSize()
			{
				if (-1 == file)
					return 0;
				struct stat statdata;
				if (-1 == ::fstat(file, &statdata))
					return 0;
				return statdata.st_size;
			}
			inline int64_t getOffset()
			{
				if (-1 == file)
					return 0;
				else
					return lseek(file, 0, SEEK_CUR);
			}
			inline int64_t setOffset(int64_t offset)
			{
				if (-1 == file)
					return 0;
				else
					return lseek(file, offset, SEEK_SET);
			}
			inline bool eof()
			{
				return getFileSize() == getOffset();
			}
		};
#endif // #ifdef LIMAX_OS_UNIX_FAMILY
	} // namespace helper { 

	FileMarshalStreamSource::FileMarshalStreamSource(const std::string& filename)
		: file(new helper::FileIO(filename, false))
	{}
	FileMarshalStreamSource::~FileMarshalStreamSource()
	{
		delete file;
	}
	void FileMarshalStreamSource::pushbytes(const void* d, size_t s)
	{
		file->write(d, s);
	}
	FileUnmarshalStreamSource::FileUnmarshalStreamSource(const std::string& filename)
		: file(new helper::FileIO(filename, true))
	{}
	FileUnmarshalStreamSource::~FileUnmarshalStreamSource()
	{
		delete file;
	}
	const void* FileUnmarshalStreamSource::pop(size_t s) const
	{
		buffer.resize(s);
		file->read(buffer);
		return buffer.begin();
	}
	void FileUnmarshalStreamSource::transaction(Transaction trans) const
	{
		switch (trans)
		{
		case Begin:
			tranpos = (size_t)file->getOffset();
			break;
		case Rollback:
			file->setOffset(tranpos);
			break;
		case Commit:
			break;
		default:
			break;
		}
	}
	bool FileUnmarshalStreamSource::eos() const
	{
		return file->eof();
	}

} // namespace limax {
